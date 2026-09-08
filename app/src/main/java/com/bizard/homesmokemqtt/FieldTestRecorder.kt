package com.bizard.homesmokemqtt

import android.content.Context
import android.os.Build
import com.bizard.homesmokecore.Telemetry
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * App-side field-test recorder. It observes Android state only and does not alter
 * Arduino commands, telemetry fields or the confirmed HomeSmoke protocol.
 */
object FieldTestRecorder {
    private const val PREFS = "homesmoke_field_test"
    private const val KEY_ACTIVE = "active"
    private const val KEY_LOG = "log_path"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_LAST_BUNDLE = "last_bundle"
    private const val DIR = "field-tests"

    @Volatile private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun isActive(): Boolean {
        val c = appContext ?: return false
        return prefs(c).getBoolean(KEY_ACTIVE, false)
    }

    @JvmStatic
    fun currentLogName(): String {
        val c = appContext ?: return ""
        return File(prefs(c).getString(KEY_LOG, "") ?: "").name
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context): File {
        init(context)
        val c = appContext ?: context.applicationContext
        val p = prefs(c)
        if (p.getBoolean(KEY_ACTIVE, false)) {
            append(c, "TEST_RESTARTED_BY_USER", JSONObject())
        }
        val dir = File(c.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val log = File(dir, "field_test_$stamp.jsonl")
        val now = System.currentTimeMillis()
        p.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_LOG, log.absolutePath)
            .putLong(KEY_STARTED_AT, now)
            .apply()
        val payload = JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER ?: "")
            .put("model", Build.MODEL ?: "")
        append(c, "TEST_STARTED", payload)
        return log
    }

    @JvmStatic
    @Synchronized
    fun record(event: String, details: String) {
        val c = appContext ?: return
        val payload = JSONObject().put("details", details)
        append(c, event, payload)
    }

    @JvmStatic
    @Synchronized
    fun recordAck(requestId: String, value: Double, ok: Boolean, reason: String) {
        val c = appContext ?: return
        val payload = JSONObject()
            .put("request_id", requestId)
            .put("ok", ok)
            .put("reason", reason)
        putFinite(payload, "value", value)
        append(c, "REMOTE_SETPOINT_ACK", payload)
    }

    @JvmStatic
    @Synchronized
    fun recordState(state: HomeSmokeService.State?, network: String, event: String) {
        val c = appContext ?: return
        val payload = JSONObject().put("network", network)
        if (state == null) {
            payload.put("service", "not_bound")
            append(c, event, payload)
            return
        }
        payload
            .put("bluetooth_connected", state.bluetoothConnected)
            .put("bluetooth_name", state.bluetoothName ?: "")
            .put("mqtt_connected", state.mqttConnected)
            .put("mqtt_state", state.mqttState ?: "")
            .put("auto_state", state.autoState?.name ?: "")
            .put("auto_status", state.autoStatus ?: "")
            .put("auto_stage_index", state.autoStageIndex)
            .put("auto_hold_ms", state.autoHoldMs)
            .put("chamber_ready", state.chamberReady)
            .put("last_error", state.lastError ?: "")
        val t = state.telemetry
        if (t != null) putTelemetry(payload, t)
        append(c, event, payload)
    }

    @JvmStatic
    @Synchronized
    fun finishAndBundle(context: Context): File {
        init(context)
        val c = appContext ?: context.applicationContext
        val p = prefs(c)
        val logPath = p.getString(KEY_LOG, "") ?: ""
        val log = File(logPath)
        if (!log.exists()) throw IllegalStateException("Журнал полевого теста ещё не создан")
        if (p.getBoolean(KEY_ACTIVE, false)) {
            append(c, "TEST_FINISHED", JSONObject())
            p.edit().putBoolean(KEY_ACTIVE, false).apply()
        }
        val dir = File(c.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val bundle = File(dir, "HomeSmoke_FieldTest_${BuildConfig.VERSION_NAME}_$stamp.zip")
        val startedAt = p.getLong(KEY_STARTED_AT, 0L)
        ZipOutputStream(FileOutputStream(bundle)).use { zip ->
            addFile(zip, log, "diagnostics/${log.name}")
            val meta = buildString {
                appendLine("HomeSmoke field test")
                appendLine("version=${BuildConfig.VERSION_NAME}")
                appendLine("versionCode=${BuildConfig.VERSION_CODE}")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("model=${Build.MODEL}")
                appendLine("startedAt=$startedAt")
                appendLine("exportedAt=${System.currentTimeMillis()}")
                appendLine("protocolChanged=false")
                appendLine("arduinoFirmwareChanged=false")
            }
            addBytes(zip, "meta.txt", meta.toByteArray(Charsets.UTF_8))
            val historyDir = File(c.filesDir, "history")
            val cutoff = if (startedAt > 60_000L) startedAt - 60_000L else 0L
            historyDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") && f.lastModified() >= cutoff }
                ?.sortedBy { it.name }
                ?.forEach { addFile(zip, it, "auto-history/${it.name}") }
        }
        p.edit().putString(KEY_LAST_BUNDLE, bundle.absolutePath).apply()
        return bundle
    }

    @JvmStatic
    fun lastBundle(context: Context): File? {
        init(context)
        val path = prefs(context.applicationContext).getString(KEY_LAST_BUNDLE, "") ?: ""
        return path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }

    private fun putTelemetry(o: JSONObject, t: Telemetry) {
        putFinite(o, "chamber", t.chamber)
        putFinite(o, "probe_k", t.probeK)
        putFinite(o, "probe_t", t.probeT)
        putFinite(o, "chamber_setpoint", t.chamberSetpoint)
        putFinite(o, "product_setpoint", t.productSetpoint)
        putFinite(o, "heater_power", t.heaterPower)
        o.put("mode", t.mode)
        o.put("last_command", t.lastCommand ?: "")
        o.put("telemetry_received_at", t.receivedAtMs)
        o.put("telemetry_age_ms", (System.currentTimeMillis() - t.receivedAtMs).coerceAtLeast(0L))
    }

    private fun putFinite(o: JSONObject, key: String, value: Double) {
        if (value.isFinite()) o.put(key, value) else o.put(key, JSONObject.NULL)
    }

    private fun append(c: Context, event: String, payload: JSONObject) {
        val p = prefs(c)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        val path = p.getString(KEY_LOG, "") ?: return
        if (path.isBlank()) return
        val file = File(path)
        file.parentFile?.mkdirs()
        val row = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("event", event)
            .put("data", payload)
            .toString() + "\n"
        FileOutputStream(file, true).use { it.write(row.toByteArray(Charsets.UTF_8)) }
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
