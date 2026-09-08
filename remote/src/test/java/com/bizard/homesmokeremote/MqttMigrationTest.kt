package com.bizard.homesmokeremote

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Checks exact MQTT bytes; no broker, Arduino or protocol extensions are involved. */
class MqttMigrationTest {
    private fun connectedClient(output: ByteArrayOutputStream): MqttClient {
        val client = MqttClient("localhost", 1883, false, "", "")
        fun field(name: String, value: Any) {
            MqttClient::class
                .java
                .getDeclaredField(name)
                .apply { isAccessible = true }
                .set(client, value)
        }
        field("connected", true)
        field(
            "socket",
            object : Socket() {
                override fun isConnected() = true
            },
        )
        field("out", output)
        return client
    }

    @Test
    fun qosOnePublishAndSubscribeKeepPacketIdsAndUtf8Bytes() {
        val output = ByteArrayOutputStream()
        val client = connectedClient(output)
        try {
            client.publish("cmd", "STOP", false)
            assertArrayEquals(
                byteArrayOf(0x32, 11, 0, 3, 99, 109, 100, 0, 1, 83, 84, 79, 80),
                output.toByteArray(),
            )
            output.reset()
            client.subscribe(" status ")
            assertArrayEquals(
                byteArrayOf(0x82.toByte(), 11, 0, 2, 0, 6, 115, 116, 97, 116, 117, 115, 1),
                output.toByteArray(),
            )
            output.reset()
            client.publish("t", "Я", true)
            assertArrayEquals(
                byteArrayOf(0x33, 7, 0, 1, 116, 0, 3, 0xd0.toByte(), 0xaf.toByte()),
                output.toByteArray(),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun incomingQosOneDecodesUtf8AndAcknowledgesUnsignedId() {
        val output = ByteArrayOutputStream()
        val client = connectedClient(output)
        var actualTopic: String? = null
        var actualPayload: String? = null
        client.setMessageListener { topic, payload ->
            actualTopic = topic
            actualPayload = payload
        }
        val body =
            byteArrayOf(0, 1, 116, 0xfe.toByte(), 0xdc.toByte()) + "Камера".toByteArray(UTF_8)
        val receive =
            MqttClient::class
                .java
                .getDeclaredMethod(
                    "handlePublish",
                    Int::class.javaPrimitiveType,
                    ByteArray::class.java,
                )
                .apply { isAccessible = true }
        try {
            receive.invoke(client, 0x32, body)
            assertEquals("t", actualTopic)
            assertEquals("Камера", actualPayload)
            assertArrayEquals(
                byteArrayOf(0x40, 2, 0xfe.toByte(), 0xdc.toByte()),
                output.toByteArray(),
            )
            output.reset()
            receive.invoke(client, 0x32, byteArrayOf(0, 9, 116))
            assertEquals(0, output.size())
        } finally {
            client.close()
        }
    }

    @Test
    fun packetIdentifierWrapsToOne() {
        val output = ByteArrayOutputStream()
        val client = connectedClient(output)
        val id =
            MqttClient::class
                .java
                .getDeclaredField("packetId")
                .apply { isAccessible = true }
                .get(client) as AtomicInteger
        try {
            id.set(65535)
            client.subscribe("t")
            client.subscribe("t")
            assertArrayEquals(
                byteArrayOf(
                    0x82.toByte(),
                    6,
                    -1,
                    -1,
                    0,
                    1,
                    116,
                    1,
                    0x82.toByte(),
                    6,
                    0,
                    1,
                    0,
                    1,
                    116,
                    1,
                ),
                output.toByteArray(),
            )
        } finally {
            client.close()
        }
    }
}
