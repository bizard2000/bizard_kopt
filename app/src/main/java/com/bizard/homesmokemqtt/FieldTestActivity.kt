package com.bizard.homesmokemqtt

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Kotlin field-test console for HomeSmoke 2.6.16.
 * It binds read-only to HomeSmokeService and records observable Android-side state.
 */
class FieldTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var service: HomeSmokeService? = null
    private var bound = false
    private var bindingRequested = false
    private var status: TextView? = null
    private var pendingBundle: File? = null
    private var lastMajorKey = ""
    private var lastNetwork = ""
    private var lastSnapshotAt = 0L
    private var hadTelemetry = false
    private var telemetryGap = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? HomeSmokeService.LocalBinder)?.getService()
            bound = service != null
            FieldTestRecorder.record("SERVICE_BOUND", "HomeSmokeService bound=$bound")
            pollNow(true)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            FieldTestRecorder.record("SERVICE_DISCONNECTED", "HomeSmokeService binder disconnected")
            pollNow(true)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            pollNow(false)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FieldTestRecorder.init(this)
        setContentView(buildUi())
        try {
            bindingRequested = bindService(Intent(this, HomeSmokeService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            FieldTestRecorder.record("SERVICE_BIND_ERROR", e.message ?: e.javaClass.simpleName)
        }
        handler.post(poll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        if (bindingRequested) {
            try { unbindService(connection) } catch (_: Exception) { }
        }
        bindingRequested = false
        bound = false
        service = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SAVE || resultCode != RESULT_OK) return
        val source = pendingBundle ?: return
        val uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            Toast.makeText(this, "Архив полевого теста сохранён", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось сохранить архив: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "HomeSmoke · Полевой тест"
            textSize = 24f
            setTextColor(Color.rgb(15, 23, 42))
        })
        root.addView(TextView(this).apply {
            text = "Kotlin-диагностика 2.6.16. Нажмите «Начать журнал», затем перед каждым реальным действием ставьте соответствующую метку. Экран можно сворачивать и открывать основное HomeSmoke или Remote, но не закрывайте его из списка последних приложений до завершения теста."
            textSize = 14f
            setTextColor(Color.rgb(71, 85, 105))
            setPadding(0, dp(8), 0, dp(12))
        })

        val statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(15, 23, 42))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        status = statusView
        root.addView(statusView, full())

        root.addView(button("Начать новый журнал") {
            try {
                val f = FieldTestRecorder.start(this)
                resetDetection()
                Toast.makeText(this, "Журнал начат: ${f.name}", Toast.LENGTH_SHORT).show()
                pollNow(true)
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Ошибка запуска журнала", Toast.LENGTH_LONG).show()
            }
        }, top(12))

        root.addView(button("Открыть основное HomeSmoke") {
            FieldTestRecorder.record("USER_OPEN_MAIN", "opening ModernHomeSmokeActivity")
            startActivity(Intent(this, ModernHomeSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }, top(8))

        root.addView(section("Метки сценариев"), top(18))
        root.addView(marker("1 · Bluetooth disconnect / reconnect", "SCENARIO_BT_DISCONNECT_RECONNECT"), top(6))
        root.addView(marker("2 · Интернет/MQTT отключить на 1–3 минуты", "SCENARIO_MQTT_NETWORK_OUTAGE"), top(6))
        root.addView(marker("3 · Remote setpoint без телеметрии", "SCENARIO_REMOTE_SETPOINT_TIMEOUT"), top(6))
        root.addView(marker("4 · Открытие HomeSmoke из foreground notification", "SCENARIO_NOTIFICATION_OPEN"), top(6))
        root.addView(marker("5 · Auto AFTER_CHAMBER_READY: выход камеры из допуска", "SCENARIO_AFTER_CHAMBER_READY"), top(6))

        root.addView(button("Завершить и сохранить ZIP") {
            try {
                pendingBundle = FieldTestRecorder.finishAndBundle(this)
                saveBundle(pendingBundle!!)
                pollNow(true)
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Ошибка экспорта", Toast.LENGTH_LONG).show()
            }
        }, top(18))

        FieldTestRecorder.lastBundle(this)?.let { last ->
            root.addView(button("Сохранить последний ZIP ещё раз") {
                pendingBundle = last
                saveBundle(last)
            }, top(8))
        }
        return scroll
    }

    private fun marker(label: String, event: String) = button(label) {
        if (!FieldTestRecorder.isActive()) {
            Toast.makeText(this, "Сначала нажмите «Начать новый журнал»", Toast.LENGTH_SHORT).show()
            return@button
        }
        FieldTestRecorder.record(event, "user marker")
        Toast.makeText(this, "Метка записана", Toast.LENGTH_SHORT).show()
        pollNow(true)
    }

    private fun pollNow(forceSnapshot: Boolean) {
        val now = System.currentTimeMillis()
        val s = service?.getState()
        val network = networkState()
        val t = s?.telemetry
        val majorKey = listOf(
            s?.bluetoothConnected,
            s?.mqttConnected,
            s?.mqttState,
            s?.autoState?.name,
            s?.autoStageIndex,
            s?.chamberReady,
            t?.mode,
            network
        ).joinToString("|")
        if (FieldTestRecorder.isActive()) {
            if (network != lastNetwork && lastNetwork.isNotEmpty()) {
                FieldTestRecorder.record("NETWORK_CHANGED", "$lastNetwork -> $network")
            }
            if (majorKey != lastMajorKey && lastMajorKey.isNotEmpty()) {
                FieldTestRecorder.recordState(s, network, "STATE_CHANGED")
            }
            if (forceSnapshot || now - lastSnapshotAt >= 5000L) {
                FieldTestRecorder.recordState(s, network, "SNAPSHOT")
                lastSnapshotAt = now
            }
            val age = t?.let { (now - it.receivedAtMs).coerceAtLeast(0L) }
            if (t != null) hadTelemetry = true
            if (hadTelemetry && !telemetryGap && (age == null || age > 8000L)) {
                telemetryGap = true
                FieldTestRecorder.record("TELEMETRY_GAP", "age_ms=${age ?: -1}")
            } else if (telemetryGap && age != null && age <= 3000L) {
                telemetryGap = false
                FieldTestRecorder.record("TELEMETRY_RESTORED", "age_ms=$age")
            }
        }
        lastMajorKey = majorKey
        lastNetwork = network
        val mode = t?.mode?.toString() ?: "—"
        val telemetryAge = t?.let { "${(now - it.receivedAtMs).coerceAtLeast(0L) / 1000}s" } ?: "—"
        status?.text = buildString {
            appendLine("Журнал: ${if (FieldTestRecorder.isActive()) "ИДЁТ" else "остановлен"}")
            appendLine("Файл: ${FieldTestRecorder.currentLogName().ifBlank { "—" }}")
            appendLine("Bluetooth: ${if (s?.bluetoothConnected == true) "OK" else "нет"}")
            appendLine("MQTT: ${s?.mqttState ?: "—"}")
            appendLine("Сеть: $network")
            appendLine("Auto: ${s?.autoState?.name ?: "—"} · этап ${(s?.autoStageIndex ?: -1) + 1}")
            appendLine("chamberReady: ${s?.chamberReady ?: false} · hold=${s?.autoHoldMs ?: 0L} ms")
            appendLine("Режим контроллера: $mode · возраст телеметрии: $telemetryAge")
            append("Ошибка: ${s?.lastError?.ifBlank { "—" } ?: "—"}")
        }
    }

    private fun resetDetection() {
        lastMajorKey = ""
        lastNetwork = ""
        lastSnapshotAt = 0L
        hadTelemetry = false
        telemetryGap = false
    }

    private fun networkState(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$type,internet=$internet,validated=$validated"
    }

    @Suppress("DEPRECATION")
    private fun saveBundle(file: File) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(i, REQ_SAVE)
    }

    private fun button(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setOnClickListener { click() }
        minHeight = dp(48)
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(Color.rgb(15, 23, 42))
        gravity = Gravity.START
    }

    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun top(marginDp: Int) = full().apply { topMargin = dp(marginDp) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQ_SAVE = 26013
    }
}
