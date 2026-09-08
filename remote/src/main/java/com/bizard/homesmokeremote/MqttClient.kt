package com.bizard.homesmokeremote

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

internal class MqttClient(
    private val host: String?,
    private val port: Int,
    private val tls: Boolean,
    username: String?,
    password: String?,
) {
    private val username: String?
    private val password: String?
    private val packetId: AtomicInteger = AtomicInteger(1)
    private var socket: Socket? = null
    private var `in`: InputStream? = null
    private var out: OutputStream? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var listener: MessageListener? = null
    private var reader: Thread? = null
    private var keepAlive: Thread? = null

    val isConnected: Boolean
        get() {
            val s: Socket? = socket
            return connected && s != null && s!!.isConnected() && !s!!.isClosed()
        }

    internal fun interface MessageListener {
        fun onMessage(topic: String?, payload: String?)
    }

    init {
        this.username = if (username == null) "" else username
        this.password = if (password == null) "" else password
    }

    fun setMessageListener(listener: MessageListener?) {
        this.listener = listener
    }

    @Synchronized
    @Throws(IOException::class)
    fun connect() {
        close()
        socket = if (tls) SSLSocketFactory.getDefault()!!.createSocket() else Socket()
        socket!!.connect(InetSocketAddress(host, port), 10000)
        socket!!.setSoTimeout(15000)
        `in` = socket!!.getInputStream()
        out = socket!!.getOutputStream()

        val b: ByteArrayOutputStream = ByteArrayOutputStream()
        writeUtf(b, "MQTT")
        b.write(4)
        var flags: Int = 0x02
        if (!username!!.isEmpty()) flags = flags or 0x80
        if (!password!!.isEmpty()) flags = flags or 0x40
        b.write(flags)
        b.write(0)
        b.write(30)
        writeUtf(b, "HomeSmokeRemote_" + java.lang.Long.toHexString(System.nanoTime())!!)
        if (!username!!.isEmpty()) writeUtf(b, username)
        if (!password!!.isEmpty()) writeUtf(b, password)
        sendPacket(0x10, b.toByteArray())

        val header: Int = `in`!!.read()
        if (header != 0x20) throw IOException("нет CONNACK")
        val remaining: Int = readRemaining(`in`)
        val ack: ByteArray? = readFully(`in`, remaining)
        if (ack!!.size < 2 || ack!![1].toInt() != 0)
            throw IOException("CONNACK=" + (if (ack!!.size > 1) ack!![1].toInt() and 255 else -1))

        socket!!.setSoTimeout(0)
        connected = true
        startReader()
        startKeepAlive()
    }

    @Synchronized
    @Throws(IOException::class)
    fun publish(topic: String?, payload: String?, retain: Boolean) {
        if (!isConnected) throw IOException("не подключен")
        val b: ByteArrayOutputStream = ByteArrayOutputStream()
        writeUtf(b, topic)
        val id: Int = nextPacketId()
        b.write((id.ushr(8)) and 255)
        b.write(id and 255)
        b.write(payload!!.toByteArray(StandardCharsets.UTF_8))
        sendPacket(0x32 or (if (retain) 1 else 0), b.toByteArray())
    }

    @Synchronized
    @Throws(IOException::class)
    fun subscribe(topic: String?) {
        if (!isConnected) throw IOException("не подключен")
        if (topic == null || topic!!.trim({ it <= ' ' }).isEmpty())
            throw IOException("пустой topic")
        val b: ByteArrayOutputStream = ByteArrayOutputStream()
        val id: Int = nextPacketId()
        b.write((id.ushr(8)) and 255)
        b.write(id and 255)
        writeUtf(b, topic!!.trim({ it <= ' ' }))
        b.write(1)
        sendPacket(0x82, b.toByteArray())
    }

    private fun nextPacketId(): Int {
        return packetId.getAndUpdate({ v -> if (v >= 65535) 1 else v + 1 })
    }

    private fun startReader() {
        reader =
            Thread(
                {
                    try {
                        while (connected) {
                            val h: Int = `in`!!.read()
                            if (h < 0) throw EOFException()
                            val n: Int = readRemaining(`in`)
                            val body: ByteArray? = readFully(`in`, n)
                            if ((h and 0xF0) == 0x30) handlePublish(h, body)
                        }
                    } catch (e: Exception) {
                        close()
                    }
                },
                "HomeSmokeRemote-MQTT-reader",
            )
        reader!!.start()
    }

    @Throws(IOException::class)
    private fun handlePublish(header: Int, body: ByteArray?) {
        if (body!!.size < 2) return
        val topicLen: Int = ((body!![0].toInt() and 255) shl 8) or (body!![1].toInt() and 255)
        if (topicLen < 0 || 2 + topicLen > body!!.size) return
        val topic: String = String(body, 2, topicLen, StandardCharsets.UTF_8)
        var p: Int = 2 + topicLen
        val qos: Int = (header shr 1) and 3
        var incomingId: Int = 0
        if (qos > 0) {
            if (p + 2 > body!!.size) return
            incomingId = ((body!![p].toInt() and 255) shl 8) or (body!![p + 1].toInt() and 255)
            p += 2
        }
        val payload: String = String(body, p, body!!.size - p, StandardCharsets.UTF_8)
        val l: MessageListener? = listener
        if (l != null)
            try {
                l!!.onMessage(topic, payload)
            } catch (ignored: Exception) {}

        if (qos == 1) {
            val ack: ByteArray? =
                byteArrayOf(((incomingId.ushr(8)) and 255).toByte(), (incomingId and 255).toByte())
            sendPacket(0x40, ack)
        }
    }

    private fun startKeepAlive() {
        keepAlive =
            Thread(
                {
                    while (connected) {
                        try {
                            Thread.sleep(20000)
                            synchronized(this@MqttClient) {
                                if (connected) sendPacket(0xC0, ByteArray(0))
                            }
                        } catch (e: Exception) {
                            close()
                            break
                        }
                    }
                },
                "HomeSmokeRemote-MQTT-keepalive",
            )
        keepAlive!!.start()
    }

    @Synchronized
    @Throws(IOException::class)
    private fun sendPacket(header: Int, body: ByteArray?) {
        if (out == null) throw IOException("socket закрыт")
        out!!.write(header)
        writeRemaining(out, body!!.size)
        out!!.write(body)
        out!!.flush()
    }

    @Synchronized
    fun close() {
        connected = false
        val s: Socket? = socket
        socket = null
        if (s != null)
            try {
                s!!.close()
            } catch (ignored: Exception) {}

        `in` = null
        out = null
        if (reader != null && reader !== Thread.currentThread()) reader!!.interrupt()
        if (keepAlive != null && keepAlive !== Thread.currentThread()) keepAlive!!.interrupt()
        reader = null
        keepAlive = null
    }

    @Throws(IOException::class)
    private fun writeUtf(out: ByteArrayOutputStream?, s: String?) {
        val b: ByteArray? = s!!.toByteArray(StandardCharsets.UTF_8)
        out!!.write((b!!.size.ushr(8)) and 255)
        out!!.write(b!!.size and 255)
        out!!.write(b)
    }

    @Throws(IOException::class)
    private fun writeRemaining(out: OutputStream?, n: Int) {
        var n = n
        do {
            var d: Int = n % 128
            n /= 128
            if (n > 0) d = d or 0x80
            out!!.write(d)
        } while (n > 0)
    }

    @Throws(IOException::class)
    private fun readRemaining(`in`: InputStream?): Int {
        var m: Int = 1
        var v: Int = 0
        var d: Int
        do {
            d = `in`!!.read()
            if (d < 0) throw EOFException()
            v += (d and 127) * m
            m *= 128
            if (m > 128 * 128 * 128 * 128) throw IOException("bad remaining length")
        } while ((d and 128) != 0)
        return v
    }

    @Throws(IOException::class)
    private fun readFully(`in`: InputStream?, n: Int): ByteArray? {
        val b: ByteArray = ByteArray(n)
        var p: Int = 0
        while (p < n) {
            val r: Int = `in`!!.read(b, p, n - p)
            if (r < 0) throw EOFException()
            p += r
        }
        return b
    }
}
