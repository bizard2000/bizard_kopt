package com.bizard.homesmokeremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Detailed view of one locally recorded smoking session. */
class SessionDetailActivity : Activity() {
    private var telemetry: TelemetryHistoryStore? = null
    private var ops: OperationalHistoryStore? = null
    private var prefs: SharedPreferences? = null
    private var session: OperationalHistoryStore.Session? = null
    private var pointText: TextView? = null

    protected override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        telemetry = TelemetryHistoryStore(this)
        ops = OperationalHistoryStore(this)
        val id: Long = getIntent()!!.getLongExtra("session_id", 0L)
        session = ops!!.querySession(id)
        if (session == null) {
            Toast.makeText(this, "Сеанс не найден", Toast.LENGTH_LONG)!!.show()
            finish()
            return
        }
        val root: View? = buildRoot()
        setContentView(root)
        applyInsets(root)
    }

    protected override fun onDestroy() {
        if (telemetry != null) telemetry!!.close()
        if (ops != null) ops!!.close()
        super.onDestroy()
    }

    private fun buildRoot(): View? {
        val root: LinearLayout = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setBackgroundColor(BG)
        root.addView(buildBar(), LinearLayout.LayoutParams(-1, dp(60)))
        val scroll: ScrollView = ScrollView(this)
        scroll.setFillViewport(true)
        scroll.setVerticalScrollBarEnabled(false)
        val page: LinearLayout = LinearLayout(this)
        page.setOrientation(LinearLayout.VERTICAL)
        page.setPadding(dp(4), dp(4), dp(4), dp(18))
        page.setBackgroundColor(BG)
        page.addView(buildSummary(), margin(8, 4, 8, 4))
        page.addView(buildAnalytics(), margin(8, 4, 8, 4))
        page.addView(buildGraph(), margin(8, 4, 8, 4))
        page.addView(buildEvents(), margin(8, 4, 8, 4))
        scroll.addView(page, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildBar(): View? {
        val bar: LinearLayout = LinearLayout(this)
        bar.setGravity(Gravity.CENTER_VERTICAL)
        bar.setPadding(dp(8), 0, dp(10), 0)
        bar.setBackgroundColor(NAVY)
        val back: TextView? = text("‹", 34, false, TEXT)
        back!!.setGravity(Gravity.CENTER)
        back!!.setOnClickListener({ v -> finish() })
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(46)))
        val titles: LinearLayout = LinearLayout(this)
        titles.setOrientation(LinearLayout.VERTICAL)
        titles.setGravity(Gravity.CENTER_VERTICAL)
        titles.addView(text("Сеанс", 18, true, TEXT))
        titles.addView(text(session!!.title(), 11, false, MUTED))
        bar.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        return bar
    }

    private fun applyInsets(root: View?) {
        if (Build.VERSION.SDK_INT < 21) return
        root!!.setOnApplyWindowInsetsListener({ view, i ->
            val l: Int
            val t: Int
            val r: Int
            val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val x: android.graphics.Insets? = i!!.getInsets(WindowInsets.Type.systemBars())
                l = x!!.left
                t = x!!.top
                r = x!!.right
                b = x!!.bottom
            } else {
                l = i!!.getSystemWindowInsetLeft()
                t = i!!.getSystemWindowInsetTop()
                r = i!!.getSystemWindowInsetRight()
                b = i!!.getSystemWindowInsetBottom()
            }
            view!!.setPadding(l, t, r, b)
            i
        })
        root!!.requestApplyInsets()
        getWindow()!!.setStatusBarColor(NAVY)
        getWindow()!!.setNavigationBarColor(BG)
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow()!!.getDecorView()!!.setSystemUiVisibility(
                getWindow()!!.getDecorView()!!.getSystemUiVisibility() or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            )
        }
    }

    private fun buildSummary(): View? {
        val c: LinearLayout? = card()
        val test: Boolean = isTestSession(session)
        val head: LinearLayout = LinearLayout(this)
        head.setGravity(Gravity.CENTER_VERTICAL)
        head.addView(text(session!!.title(), 18, true, TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        val label: String =
            if (test) "ТЕСТ" else (if (session!!.active()) "АКТИВЕН" else "ЗАВЕРШЁН")
        val color: Int = if (test) ORANGE else (if (session!!.active()) GREEN else OFF)
        val chip: TextView? = text(label, 10, true, Color.WHITE)
        chip!!.setGravity(Gravity.CENTER)
        chip!!.setPadding(dp(8), dp(4), dp(8), dp(4))
        chip!!.setBackground(round(color, 12))
        head.addView(chip)
        c!!.addView(head)
        if (test) c!!.addView(detail("Источник · локальная симуляция"))
        val end: Long =
            if (session!!.active()) System.currentTimeMillis() else session!!.effectiveEnd()
        c!!.addView(detail("Начало · " + dateTime(session!!.startTs)!!))
        c!!.addView(detail("Конец · " + (if (session!!.active()) "идёт сейчас" else dateTime(end))))
        c!!.addView(
            detail(
                "Длительность · " +
                    duration(Math.max(0, end - session!!.startTs)) +
                    " · точек " +
                    session!!.samples
            )
        )
        c!!.addView(detail("Камера · " + range(session!!.cameraMin, session!!.cameraMax, " °C")!!))
        c!!.addView(
            detail(
                "Щуп K · " +
                    range(session!!.kMin, session!!.kMax, " °C") +
                    " · Щуп T · " +
                    range(session!!.tMin, session!!.tMax, " °C")
            )
        )
        c!!.addView(
            detail(
                "Макс. ТЭН · " +
                    value(session!!.heaterMax, " %") +
                    (if (session!!.outageMs > 0)
                        " · потери связи ≈ " + durationDetailed(session!!.outageMs)!!
                    else " · потерь связи не зафиксировано")
            )
        )
        val row: LinearLayout = LinearLayout(this)
        val csv: Button? = action("Экспорт CSV", BLUE)
        val json: Button? = action("Экспорт JSON", ORANGE)
        csv!!.setOnClickListener({ v -> requestExport("csv") })
        json!!.setOnClickListener({ v -> requestExport("json") })
        val a: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        a.setMargins(0, dp(8), dp(4), 0)
        val b: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        b.setMargins(dp(4), dp(8), 0, 0)
        row.addView(csv, a)
        row.addView(json, b)
        c!!.addView(row)
        return c
    }

    private fun buildAnalytics(): View? {
        val c: LinearLayout? = card()
        c!!.addView(text("Итог сеанса", 18, true, TEXT))
        val end: Long =
            if (session!!.active()) System.currentTimeMillis() else session!!.effectiveEnd()
        val samples: List<TelemetryHistoryStore.Sample?>? =
            telemetry!!.query(session!!.startTs, end, 0)
        val a: SessionAnalytics.Result? = SessionAnalytics.analyze(samples)
        if (!a!!.available) {
            val note: TextView? =
                detail(
                    "Расширенная аналитика недоступна: сырые точки этого сеанса уже удалены из 24-часовой локальной истории. Базовая сводка выше сохранена."
                )
            note!!.setPadding(0, dp(8), 0, dp(8))
            c!!.addView(note)
            return c
        }
        if (isTestSession(session)) {
            val test: TextView? =
                detail(
                    "ТЕСТ · синтетические данные проверяют расчёты; это не оценка реальной коптильни."
                )
            test!!.setTextColor(ORANGE)
            test!!.setTypeface(Typeface.DEFAULT_BOLD)
            c!!.addView(test)
        }
        c!!.addView(
            detail(
                "Средняя камера · " +
                    num(a!!.averageCamera) +
                    " °C · средний ТЭН · " +
                    num(a!!.averageHeater) +
                    " %"
            )
        )
        c!!.addView(
            detail(
                "Стабильность · ±1 °C " +
                    pct(a!!.stability1) +
                    " · ±2 °C " +
                    pct(a!!.stability2) +
                    " · ±3 °C " +
                    pct(a!!.stability3)
            )
        )
        c!!.addView(
            detail(
                "Среднее отклонение · " +
                    num(a!!.averageAbsoluteError) +
                    " °C · макс. перегрев · " +
                    signed(a!!.maxOvershoot) +
                    " °C"
            )
        )
        val target: String? =
            if (java.lang.Double.isNaN(a!!.initialSetpoint)) "нет корректной уставки"
            else
                num(a!!.initialSetpoint) +
                    " °C · " +
                    (if (a!!.timeToFirstTargetMs >= 0)
                        "достигнута через " + durationDetailed(a!!.timeToFirstTargetMs)!!
                    else "не достигнута в пределах ±1 °C")
        c!!.addView(detail("Первая уставка · " + target!!))
        c!!.addView(
            detail(
                "Смен уставки · " +
                    a!!.setpointChanges +
                    " · полезной телеметрии · " +
                    durationDetailed(a!!.validTelemetryMs)
            )
        )
        c!!.addView(
            detail(
                "Изменение щупов · K " +
                    signed(a!!.probeKDelta) +
                    " °C · T " +
                    signed(a!!.probeTDelta) +
                    " °C"
            )
        )
        c!!.addView(
            detail(
                "Потери телеметрии · " +
                    a!!.outageEpisodes +
                    " эпиз. · ≈ " +
                    durationDetailed(a!!.outageMs)
            )
        )
        val method: TextView? =
            detail(
                "Расчёт по сохранённым точкам. Интервалы между точками >15 сек считаются потерей телеметрии и исключаются из средних и показателей стабильности. Интервал смены уставки также не смешивается со стабильностью."
            )
        method!!.setPadding(0, dp(7), 0, 0)
        c!!.addView(method)
        return c
    }

    private fun buildGraph(): View? {
        val c: LinearLayout? = card()
        c!!.addView(text("График сеанса", 18, true, TEXT))
        val end: Long =
            if (session!!.active()) System.currentTimeMillis() else session!!.effectiveEnd()
        val samples: List<TelemetryHistoryStore.Sample?>? =
            telemetry!!.query(session!!.startTs, end, 700)
        if (samples!!.isEmpty()) {
            val note: TextView? =
                detail(
                    "Сырые точки этого сеанса уже недоступны. Локальная телеметрия хранится до 24 часов, но сводка сеанса остаётся в журнале."
                )
            note!!.setPadding(0, dp(12), 0, dp(12))
            c!!.addView(note)
            return c
        }
        val current: TextView? =
            detail(
                "Точек на графике · " +
                    samples!!.size.toString() +
                    " · " +
                    time(samples!!.get(0)!!.ts) +
                    "—" +
                    time(samples!!.get(samples!!.size - 1)!!.ts)
            )
        current!!.setPadding(0, dp(5), 0, dp(4))
        c!!.addView(current)
        val chart: TemperatureChartView = TemperatureChartView(this)
        chart.setSeries(true, true, true, true)
        chart.setData(samples)
        c!!.addView(chart, LinearLayout.LayoutParams(-1, dp(310)))
        pointText = detail("Коснитесь графика, чтобы увидеть значения точки.")
        pointText!!.setPadding(0, dp(5), 0, 0)
        c!!.addView(pointText)
        chart.setOnSelectionListener({ s ->
            pointText!!.setText(
                dateTime(s!!.ts) +
                    "\nКамера " +
                    num(s!!.camera) +
                    " °C · Уставка " +
                    num(s!!.setpoint) +
                    " °C\nK " +
                    num(s!!.probeK) +
                    " °C · T " +
                    num(s!!.probeT) +
                    " °C · ТЭН " +
                    num(s!!.heater) +
                    " %"
            )
        })
        return c
    }

    private fun buildEvents(): View? {
        val c: LinearLayout? = card()
        c!!.addView(text("События этого сеанса", 18, true, TEXT))
        val end: Long =
            if (session!!.active()) System.currentTimeMillis() else session!!.effectiveEnd() + 5000L
        val events: List<OperationalHistoryStore.Event?>? =
            ops!!.queryEvents(Math.max(0, session!!.startTs - 2000L), end, 200)
        if (events!!.isEmpty()) {
            c!!.addView(detail("Событий для этого сеанса не найдено"))
            return c
        }
        val f: SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        for (e: OperationalHistoryStore.Event? in events!!) {
            val row: TextView? =
                text(f.format(Date(e!!.ts)) + " · " + e!!.message, 12, false, eventColor(e!!.type))
            row!!.setPadding(0, dp(5), 0, dp(5))
            c!!.addView(row)
        }
        return c
    }

    private fun requestExport(format: String?) {
        val i: Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.setType(if ("csv" == format) "text/csv" else "application/json")
        val stamp: String? =
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(session!!.startTs))
        i.putExtra(Intent.EXTRA_TITLE, "HomeSmoke_Remote_session_" + stamp + "." + format)
        startActivityForResult(i, if ("csv" == format) REQ_CSV else REQ_JSON)
    }

    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null || data!!.getData() == null) return
        val format: String = if (requestCode == REQ_CSV) "csv" else "json"
        try {
            writeExport(data!!.getData(), format)
            Toast.makeText(this, "Экспорт сохранён", Toast.LENGTH_SHORT)!!.show()
            ops!!.addEvent(
                System.currentTimeMillis(),
                "info",
                "Экспортирован сеанс · " + format.uppercase(Locale.ROOT)!!,
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка экспорта: " + safe(e)!!, Toast.LENGTH_LONG)!!.show()
        }
    }

    @Throws(Exception::class)
    private fun writeExport(uri: Uri?, format: String?) {
        val end: Long =
            if (session!!.active()) System.currentTimeMillis() else session!!.effectiveEnd()
        val samples: List<TelemetryHistoryStore.Sample?>? =
            telemetry!!.query(session!!.startTs, end, 0)
        val analytics: SessionAnalytics.Result? = SessionAnalytics.analyze(samples)
        val os: OutputStream? = getContentResolver()!!.openOutputStream(uri!!)
        if (os == null) throw IllegalStateException("Не удалось открыть файл")
        OutputStreamWriter(os, "UTF-8")
            .use({ w ->
                if ("csv" == format) writeCsv(w, samples, analytics)
                else writeJson(w, samples, analytics)
            })
    }

    @Throws(Exception::class)
    private fun writeCsv(
        w: OutputStreamWriter?,
        samples: List<TelemetryHistoryStore.Sample?>?,
        a: SessionAnalytics.Result?,
    ) {
        val iso: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        w!!.write("source," + (if (isTestSession(session)) "test" else "live") + "\n")
        w!!.write("session_start," + iso.format(Date(session!!.startTs)) + "\n")
        w!!.write("session_end," + iso.format(Date(session!!.effectiveEnd())) + "\n")
        w!!.write("duration_minutes," + (session!!.durationMs() / 60000L) + "\n")
        w!!.write("outage_seconds," + (session!!.outageMs / 1000L) + "\n")
        if (a!!.available) {
            w!!.write("analytics_average_camera_c," + csv(a!!.averageCamera) + "\n")
            w!!.write("analytics_average_heater_pct," + csv(a!!.averageHeater) + "\n")
            w!!.write("analytics_average_abs_error_c," + csv(a!!.averageAbsoluteError) + "\n")
            w!!.write("analytics_max_overshoot_c," + csv(a!!.maxOvershoot) + "\n")
            w!!.write("analytics_stability_1c_pct," + csv(a!!.stability1) + "\n")
            w!!.write("analytics_stability_2c_pct," + csv(a!!.stability2) + "\n")
            w!!.write("analytics_stability_3c_pct," + csv(a!!.stability3) + "\n")
            w!!.write("analytics_initial_setpoint_c," + csv(a!!.initialSetpoint) + "\n")
            w!!.write(
                "analytics_time_to_first_target_s," +
                    (if (a!!.timeToFirstTargetMs >= 0) a!!.timeToFirstTargetMs / 1000L else "") +
                    "\n"
            )
            w!!.write("analytics_setpoint_changes," + a!!.setpointChanges + "\n")
            w!!.write("analytics_probe_k_delta_c," + csv(a!!.probeKDelta) + "\n")
            w!!.write("analytics_probe_t_delta_c," + csv(a!!.probeTDelta) + "\n")
            w!!.write("analytics_outage_episodes," + a!!.outageEpisodes + "\n")
            w!!.write("analytics_outage_seconds," + (a!!.outageMs / 1000L) + "\n")
        }
        w!!.write("\ntimestamp,camera_c,setpoint_c,probe_k_c,probe_t_c,heater_pct\n")
        for (x: TelemetryHistoryStore.Sample? in samples!!) w!!.write(
            iso.format(Date(x!!.ts)) +
                "," +
                csv(x!!.camera) +
                "," +
                csv(x!!.setpoint) +
                "," +
                csv(x!!.probeK) +
                "," +
                csv(x!!.probeT) +
                "," +
                csv(x!!.heater) +
                "\n"
        )
        if (samples!!.isEmpty())
            w!!.write("# raw samples unavailable (local telemetry is retained for 24 hours)\n")
    }

    @Throws(Exception::class)
    private fun writeJson(
        w: OutputStreamWriter?,
        samples: List<TelemetryHistoryStore.Sample?>?,
        a: SessionAnalytics.Result?,
    ) {
        val root: JSONObject = JSONObject()
        val summary: JSONObject = JSONObject()
        summary.put("source", if (isTestSession(session)) "test" else "live")
        summary.put("start_ts", session!!.startTs)
        summary.put("end_ts", session!!.effectiveEnd())
        summary.put("duration_ms", session!!.durationMs())
        summary.put("samples", session!!.samples)
        summary.put("outage_ms", session!!.outageMs)
        put(summary, "camera_min", session!!.cameraMin)
        put(summary, "camera_max", session!!.cameraMax)
        put(summary, "probe_k_min", session!!.kMin)
        put(summary, "probe_k_max", session!!.kMax)
        put(summary, "probe_t_min", session!!.tMin)
        put(summary, "probe_t_max", session!!.tMax)
        put(summary, "heater_max", session!!.heaterMax)
        root.put("session", summary)
        if (a!!.available) {
            val x: JSONObject = JSONObject()
            put(x, "average_camera_c", a!!.averageCamera)
            put(x, "average_heater_pct", a!!.averageHeater)
            put(x, "average_abs_error_c", a!!.averageAbsoluteError)
            put(x, "max_overshoot_c", a!!.maxOvershoot)
            put(x, "stability_1c_pct", a!!.stability1)
            put(x, "stability_2c_pct", a!!.stability2)
            put(x, "stability_3c_pct", a!!.stability3)
            put(x, "initial_setpoint_c", a!!.initialSetpoint)
            if (a!!.timeToFirstTargetMs >= 0)
                x.put("time_to_first_target_ms", a!!.timeToFirstTargetMs)
            else x.put("time_to_first_target_ms", JSONObject.NULL)
            x.put("setpoint_changes", a!!.setpointChanges)
            put(x, "probe_k_delta_c", a!!.probeKDelta)
            put(x, "probe_t_delta_c", a!!.probeTDelta)
            x.put("outage_episodes", a!!.outageEpisodes)
            x.put("outage_ms", a!!.outageMs)
            x.put("valid_telemetry_ms", a!!.validTelemetryMs)
            root.put("analytics", x)
        }
        val arr: JSONArray = JSONArray()
        for (p: TelemetryHistoryStore.Sample? in samples!!) {
            val o: JSONObject = JSONObject()
            o.put("ts", p!!.ts)
            put(o, "camera", p!!.camera)
            put(o, "setpoint", p!!.setpoint)
            put(o, "probe_k", p!!.probeK)
            put(o, "probe_t", p!!.probeT)
            put(o, "heater", p!!.heater)
            arr.put(o)
        }
        root.put("telemetry", arr)
        w!!.write(root.toString(2))
    }

    private fun isTestSession(s: OperationalHistoryStore.Session?): Boolean {
        val start: Long = s!!.startTs
        val end: Long = if (s!!.active()) System.currentTimeMillis() else s!!.effectiveEnd()
        if (prefs!!.getBoolean("test_mode_active", false)) {
            val active: Long = prefs!!.getLong("test_active_start", 0L)
            if (active > 0 && start <= System.currentTimeMillis() && end >= active) return true
        }
        try {
            val a: JSONArray = JSONArray(prefs!!.getString("test_intervals", "[]"))
            for (i: Int in 0 until a.length()) {
                val x: JSONObject? = a.optJSONObject(i)
                if (x == null) continue
                val ts: Long = x!!.optLong("start", 0)
                val te: Long = x!!.optLong("end", 0)
                if (ts > 0 && te >= ts && start <= te && end >= ts) return true
            }
        } catch (ignored: Exception) {}

        return false
    }

    private fun eventColor(type: String?): Int {
        if ("error" == type) return Color.rgb(190, 40, 40)
        if ("connection" == type || "test" == type) return ORANGE
        if ("command" == type) return BLUE
        if ("session" == type) return GREEN
        return TEXT
    }

    private fun card(): LinearLayout? {
        val c: LinearLayout = LinearLayout(this)
        c.setOrientation(LinearLayout.VERTICAL)
        c.setPadding(dp(12), dp(10), dp(12), dp(10))
        c.setBackground(roundStroke(CARD, 18, BORDER, 1))
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1).toFloat())
        return c
    }

    private fun detail(s: String?): TextView? {
        val t: TextView? = text(s, 12, false, MUTED)
        t!!.setPadding(0, dp(4), 0, 0)
        t!!.setLineSpacing(0f, 1.06f)
        return t
    }

    private fun action(s: String?, color: Int): Button? {
        val b: Button = Button(this)
        b.setText(s)
        b.setTextSize(12f)
        b.setTypeface(Typeface.DEFAULT_BOLD)
        b.setTextColor(Color.WHITE)
        b.setAllCaps(false)
        b.setGravity(Gravity.CENTER)
        b.setPadding(dp(4), 0, dp(4), 0)
        b.setBackground(round(color, 13))
        return b
    }

    private fun text(s: String?, sp: Int, bold: Boolean, color: Int): TextView? {
        val t: TextView = TextView(this)
        t.setText(s)
        t.setTextSize(sp.toFloat())
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
        return t
    }

    private fun round(color: Int, radius: Int): GradientDrawable? {
        val g: GradientDrawable = GradientDrawable()
        g.setColor(color)
        g.setCornerRadius(dp(radius).toFloat())
        return g
    }

    private fun roundStroke(color: Int, radius: Int, stroke: Int, width: Int): GradientDrawable? {
        val g: GradientDrawable? = round(color, radius)
        g!!.setStroke(dp(width), stroke)
        return g
    }

    private fun margin(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun dp(v: Int): Int {
        return Math.round(v * getResources()!!.getDisplayMetrics()!!.density)
    }

    companion object {
        private val NAVY: Int = Color.WHITE
        private val BG: Int = Color.rgb(245, 244, 240)
        private val CARD: Int = Color.WHITE
        private val TEXT: Int = Color.rgb(32, 42, 39)
        private val MUTED: Int = Color.rgb(98, 110, 104)
        private val BORDER: Int = Color.rgb(226, 229, 224)
        private val GREEN: Int = Color.rgb(40, 101, 76)
        private val BLUE: Int = Color.rgb(165, 72, 34)
        private val ORANGE: Int = Color.rgb(197, 101, 16)
        private val OFF: Int = Color.rgb(116, 129, 145)
        private val REQ_CSV: Int = 4201
        private val REQ_JSON: Int = 4202

        @Throws(Exception::class)
        private fun put(o: JSONObject?, k: String?, v: Double) {
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v))
                o!!.put(k, JSONObject.NULL)
            else o!!.put(k, v)
        }

        private fun csv(v: Double): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) ""
            else String.format(Locale.US, "%.3f", v)
        }

        private fun num(v: Double): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) "—"
            else String.format(Locale.getDefault(), "%.1f", v)
        }

        private fun pct(v: Double): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) "—"
            else String.format(Locale.getDefault(), "%.0f %%", v)
        }

        private fun signed(v: Double): String? {
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) return "—"
            return String.format(Locale.getDefault(), if (v >= 0) "+%.1f" else "%.1f", v)
        }

        private fun range(min: Double, max: Double, suffix: String?): String? {
            if (java.lang.Double.isNaN(min) || java.lang.Double.isNaN(max)) return "—"
            return num(min) + "…" + num(max) + suffix
        }

        private fun value(v: Double, suffix: String?): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) "—"
            else num(v)!! + suffix!!
        }

        private fun duration(ms: Long): String? {
            val m: Long = Math.max(0, ms / 60000L)
            val h: Long = m / 60
            val r: Long = m % 60
            return if (h > 0) h.toString() + " ч " + r.toString() + " мин"
            else m.toString() + " мин"
        }

        private fun durationDetailed(ms: Long): String? {
            val sec: Long = Math.max(0, ms / 1000L)
            val h: Long = sec / 3600
            val m: Long = (sec % 3600) / 60
            val s: Long = sec % 60
            if (h > 0) return h.toString() + " ч " + m.toString() + " мин"
            if (m > 0) return m.toString() + " мин " + s.toString() + " сек"
            return s.toString() + " сек"
        }

        private fun dateTime(ts: Long): String? {
            return SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
        }

        private fun time(ts: Long): String? {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
        }

        private fun safe(t: Throwable?): String? {
            val s: String? = if (t == null) "" else t!!.message
            return if (s == null || s!!.trim({ it <= ' ' }).isEmpty()) t!!.javaClass.getSimpleName()
            else s
        }
    }
}
