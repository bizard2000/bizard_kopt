package com.bizard.homesmokeremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Keeps the MQTT subscription and telemetry journal alive independently of an Activity.
 * No Arduino protocol or command behavior is changed here.
 */
class RemoteMqttService : Service() {
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Remote-MQTT-background") }
    private lateinit var history: TelemetryHistoryStore
    private lateinit var operations: OperationalHistoryStore
    @Volatile private var running = false
    @Volatile private var connecting = false
    @Volatile private var connectedClient: MqttClient? = null
    @Volatile private var statusTopic = "homesmoke/status"
    @Volatile private var lastStatus: String? = null
    private var scheduled = false
    private var lastFailureLoggedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        history = TelemetryHistoryStore(this)
        operations = OperationalHistoryStore(this)
        active = this
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL, "HomeSmoke Remote — фон",
                        NotificationManager.IMPORTANCE_LOW)
                )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        if ((intent == null && !prefs.getBoolean("background_monitor_enabled", false))
            || prefs.getBoolean("test_mode_active", false)) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (e: RuntimeException) {
            Log.e(TAG, "Foreground service failed to start", e)
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        prefs.edit().putBoolean("background_monitor_enabled", true).apply()
        if (!scheduled) {
            scheduled = true
            executor.scheduleWithFixedDelay({ poll() }, 0, 5, TimeUnit.SECONDS)
        }
        if (intent?.action == ACTION_RECONNECT) executor.execute {
            val c = connectedClient
            connectedClient = null
            c?.close()
            poll()
        }
        return START_STICKY
    }

    private fun poll() {
        if (!running) return
        val previous = connectedClient
        if (previous != null && previous.isConnected) return
        if (previous != null) {
            connectedClient = null
            previous.close()
            operations.addEvent(System.currentTimeMillis(), "connection",
                "MQTT: соединение потеряно, переподключение")
        }
        if (connecting) return
        connecting = true
        try {
            val prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
            val host = prefs.getString("broker", "")?.trim().orEmpty()
            val port = prefs.getString("port", "1883")?.toIntOrNull()
            if (host.isEmpty() || port == null || port !in 1..65535) return
            val st = prefs.getString("status_topic", "homesmoke/status")
                .orEmpty().ifBlank { "homesmoke/status" }
            val at = prefs.getString("ack_topic", "homesmoke/ack")
                .orEmpty().ifBlank { "homesmoke/ack" }
            val client = MqttClient(
                host, port, prefs.getBoolean("tls", false),
                prefs.getString("user", ""), SecretStore(this).get()
            )
            client.setMessageListener { topic, payload ->
                if (!running) return@setMessageListener
                if (topic == st && payload != null) {
                    lastStatus = payload
                    try {
                        freshSample(payload, System.currentTimeMillis())?.let {
                            history.addFresh(it)
                            operations.processSamples(listOf(it))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to store MQTT telemetry", e)
                    }
                }
                observer?.onMessage(topic, payload)
            }
            try {
                client.connect()
                client.subscribe(st)
                client.subscribe(at)
            } catch (e: Exception) {
                client.close()
                throw e
            }
            if (!running) {
                client.close()
                return
            }
            statusTopic = st
            connectedClient = client
            operations.addEvent(System.currentTimeMillis(), "connection",
                "MQTT: брокер подключён")
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            if (now - lastFailureLoggedAt > 60_000) {
                lastFailureLoggedAt = now
                operations.addEvent(now, "connection", "MQTT: подключение недоступно")
                Log.w(TAG, "Reconnect failed", e)
            }
        } finally {
            connecting = false
        }
    }

    private fun notification(): Notification {
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, GraphUxFixActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RemoteMqttService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HomeSmoke Remote")
            .setContentText("Фоновый MQTT-мониторинг коптильни")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stop)
            .build()
    }

    private fun shutdown() {
        running = false
        getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE).edit()
            .putBoolean("background_monitor_enabled", false).apply()
        val c = connectedClient
        connectedClient = null
        c?.close()
    }

    override fun onDestroy() {
        shutdown()
        executor.shutdownNow()
        if (active === this) active = null
        history.close()
        operations.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HomeSmokeRemoteMQTT"
        private const val CHANNEL = "homesmoke_remote_background"
        private const val NOTIFICATION_ID = 2240
        private const val ACTION_START = "com.bizard.homesmokeremote.START"
        private const val ACTION_STOP = "com.bizard.homesmokeremote.STOP"
        private const val ACTION_RECONNECT = "com.bizard.homesmokeremote.RECONNECT"
        @Volatile private var active: RemoteMqttService? = null
        @Volatile private var observer: MqttClient.MessageListener? = null

        internal fun client(): MqttClient? = active?.connectedClient
        internal fun isConnecting(): Boolean = active?.connecting == true

        internal fun observe(listener: MqttClient.MessageListener?) {
            observer = listener
            if (listener != null) {
                val s = active
                val cached = s?.lastStatus
                if (cached != null) listener.onMessage(s.statusTopic, cached)
            }
        }

        internal fun start(context: Context, reconnect: Boolean = false) {
            val intent = Intent(context, RemoteMqttService::class.java)
                .setAction(if (reconnect) ACTION_RECONNECT else ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        internal fun stop(context: Context) {
            active?.shutdown()
            context.stopService(Intent(context, RemoteMqttService::class.java))
        }

        internal fun freshSample(payload: String, now: Long): TelemetryHistoryStore.Sample? {
            val o = JSONObject(payload)
            var ts = o.optLong("ts", 0L)
            if (ts <= 0) ts = now
            else if (ts < 100_000_000_000L) ts *= 1000L
            if (ts > now + 5L * 60L * 1000L) ts = now
            if (kotlin.math.abs(now - ts) > 10_000L) return null
            fun num(key: String) =
                o.optString(key, "").trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN
            return TelemetryHistoryStore.Sample(ts, num("temp_ds"), num("temp_k"),
                num("temp_tip_k"), num("temp_tip_t"), num("heater_power"))
        }
    }
}
