package com.bizard.homesmokemqtt

import android.content.Context
import com.bizard.homesmokecore.Telemetry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Local manual journal. Stage changes never issue device commands. */
class CookingJournal(context: Context) {
    private val activeFile = File(context.filesDir, "manual_cooking_active.json")
    private val directory = File(context.filesDir, "cooking-journals")
    private var session: JSONObject? = runCatching {
        if (activeFile.exists()) JSONObject(activeFile.readText(Charsets.UTF_8)) else null
    }.getOrNull()

    @Synchronized fun isActive(): Boolean = session != null
    @Synchronized fun snapshot(): JSONObject? = session?.let { JSONObject(it.toString()) }

    private fun values(t: Telemetry?): JSONObject = JSONObject().apply {
        if (t == null) put("missingTelemetry", true) else {
            put("sampleAt", t.receivedAtMs)
            putNumber(this, "chamber", t.chamber)
            putNumber(this, "setpoint", t.chamberSetpoint)
            putNumber(this, "probeK", t.probeK)
            putNumber(this, "probeT", t.probeT)
        }
    }
    private fun putNumber(j: JSONObject, key: String, n: Double) {
        j.put(key, if (n.isFinite()) n else JSONObject.NULL)
    }
    private fun begin(name: String, t: Telemetry?) {
        session!!.getJSONArray("stages").put(JSONObject()
            .put("name", name).put("startedAt", System.currentTimeMillis())
            .put("start", values(t)).put("samples", JSONArray()))
    }
    @Synchronized fun start(title: String, firstStage: String, t: Telemetry?) {
        check(session == null) { "Приготовление уже записывается" }
        session = JSONObject().put("name", title).put("startedAt", System.currentTimeMillis())
            .put("stages", JSONArray())
        begin(firstStage, t)
        save()
    }
    @Synchronized fun sample(t: Telemetry?) {
        if (session == null || t == null) return
        val stages = session!!.getJSONArray("stages")
        val samples = stages.getJSONObject(stages.length() - 1).getJSONArray("samples")
        if (samples.length() > 0 &&
            t.receivedAtMs - samples.getJSONObject(samples.length() - 1).optLong("sampleAt") < 5000L) return
        samples.put(values(t))
        save()
    }
    private fun closeStage(t: Telemetry?) {
        val stages = session!!.getJSONArray("stages")
        stages.getJSONObject(stages.length() - 1)
            .put("endedAt", System.currentTimeMillis()).put("end", values(t))
    }
    @Synchronized fun next(name: String, t: Telemetry?) {
        check(session != null) { "Нет активного приготовления" }
        closeStage(t)
        begin(name, t)
        save()
    }
    @Synchronized fun finish(t: Telemetry?): File {
        check(session != null) { "Нет активного приготовления" }
        closeStage(t)
        val completed = session!!.put("endedAt", System.currentTimeMillis())
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать каталог журнала" }
        val result = File(directory, "cooking_${completed.getLong("startedAt")}.json")
        write(result, completed.toString(2))
        check(!activeFile.exists() || activeFile.delete()) { "Не удалось очистить активный журнал" }
        session = null
        return result
    }
    private fun save() = write(activeFile, session!!.toString())
    private fun write(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        check(tmp.renameTo(file)) { "Не удалось сохранить журнал" }
    }
}
