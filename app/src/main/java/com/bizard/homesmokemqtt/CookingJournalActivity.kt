package com.bizard.homesmokemqtt

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class CookingJournalActivity : Activity() {
    private lateinit var journal: CookingJournal
    private var service: HomeSmokeService? = null
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as HomeSmokeService.LocalBinder).service
            journal = service!!.cookingJournal()
            refresh()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; refresh() }
    }
    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 5000) }
    }
    private fun telemetry() = service?.state?.telemetry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        journal = CookingJournal(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply { textSize = 18f }
        root.addView(status)
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Начать приготовление") {
            ask("Название приготовления", "Рёбра") { title ->
                ask("Первый этап", "Сушка") { stage -> act { journal.start(title, stage, telemetry()) } }
            }
        }
        button("Следующий этап") {
            ask("Следующий этап", "Копчение") { name -> act { journal.next(name, telemetry()) } }
        }
        button("Завершить запись") {
            AlertDialog.Builder(this).setTitle("Завершить приготовление?")
                .setMessage("Завершится только журнал. Нагрев камеры не выключается. Используйте STOP на главном экране.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Завершить") { _, _ -> act { journal.finish(telemetry()) } }.show()
        }
        button("Сохранённые приготовления") { showSaved() }
        root.addView(TextView(this).apply {
            text = "Этапы создаются только кнопкой. Изменение уставки не создаёт этап."
        })
        setContentView(root)
        bindService(Intent(this, HomeSmokeService::class.java), connection, BIND_AUTO_CREATE)
        refresh()
    }
    private fun ask(title: String, hint: String, callback: (String) -> Unit) {
        val input = EditText(this).apply { isSingleLine = true; setHint(hint) }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty()) Toast.makeText(this, "Введите название", Toast.LENGTH_LONG).show()
                else callback(value)
            }.show()
    }
    private fun act(action: () -> Unit) {
        try { action(); refresh() }
        catch (e: Exception) {
            AlertDialog.Builder(this).setMessage(e.message).setPositiveButton("OK", null).show()
        }
    }
    private fun refresh() {
        if (!::status.isInitialized) return
        val j = journal.snapshot()
        if (j == null) { status.text = "Нет активного приготовления"; return }
        val stages = j.optJSONArray("stages")
        val last = if (stages != null && stages.length() > 0) stages.optJSONObject(stages.length() - 1) else null
        status.text = "Приготовление: ${j.optString("name")}\nЭтап ${stages?.length() ?: 0}: ${last?.optString("name") ?: ""}\nТелеметрия: ${if (telemetry() == null) "не получена" else "подключена"}"
    }
    private fun showSaved() {
        val files = File(filesDir, "cooking-journals").listFiles { f -> f.name.startsWith("cooking_") && f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            AlertDialog.Builder(this).setMessage("Сохранённых приготовлений пока нет").setPositiveButton("OK", null).show()
            return
        }
        val labels = files.map {
            runCatching {
                val j = JSONObject(it.readText())
                j.optString("name") + " — " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(j.optLong("startedAt")))
            }.getOrDefault(it.name)
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Приготовления").setItems(labels) { _, index ->
            val j = JSONObject(files[index].readText())
            val stages = j.getJSONArray("stages")
            val details = StringBuilder()
            for (i in 0 until stages.length()) {
                val stage = stages.getJSONObject(i)
                details.append("\n${i + 1}. ${stage.optString("name")}\n")
                details.append("Камера: ${stage.optJSONObject("start")?.optString("chamber", "—")} → ${stage.optJSONObject("end")?.optString("chamber", "—")} °C\n")
            }
            AlertDialog.Builder(this).setTitle(j.optString("name")).setMessage(details.toString()).setPositiveButton("OK", null).show()
        }.show()
    }
    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }
    override fun onDestroy() { if (service != null) unbindService(connection); super.onDestroy() }
}
