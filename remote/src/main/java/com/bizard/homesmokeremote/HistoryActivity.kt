package com.bizard.homesmokeremote

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.HorizontalScrollView
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

/** Local sessions, operational journal, filters and per-session export. */
class HistoryActivity : Activity() {
    private lateinit var palette: RemotePalette
    private var telemetry: TelemetryHistoryStore? = null
    private var ops: OperationalHistoryStore? = null
    private var prefs: SharedPreferences? = null
    private var exportSession: OperationalHistoryStore.Session? = null
    private var eventFilter: String? = "Все"
    private var filterBar: LinearLayout? = null
    private var journalRows: LinearLayout? = null

    protected override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        palette = RemoteTheme.palette(this)
        prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        telemetry = TelemetryHistoryStore(this)
        ops = OperationalHistoryStore(this)
        synchronize()
        val root: View? = buildRoot()
        setContentView(root)
        applyInsets(root)
    }

    protected override fun onResume() {
        super.onResume()
        if (ops != null) {
            synchronize()
            renderJournal()
        }
    }

    protected override fun onDestroy() {
        if (telemetry != null) telemetry!!.close()
        if (ops != null) ops!!.close()
        super.onDestroy()
    }

    private fun synchronize() {
        val now: Long = System.currentTimeMillis()
        val last: Long = ops!!.lastProcessedTs
        val samples: List<TelemetryHistoryStore.Sample?>? =
            telemetry!!.query(Math.max(now - 24L * 60L * 60L * 1000L, last + 1L), now, 0)
        ops!!.processSamples(samples)
        ops!!.closeIfInactive(now)
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
        buildSessions(page)
        buildEvents(page)
        scroll.addView(page, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildBar(): View? {
        val bar: LinearLayout = LinearLayout(this)
        bar.setGravity(Gravity.CENTER_VERTICAL)
        bar.setPadding(dp(8), 0, dp(8), 0)
        bar.setBackgroundColor(NAVY)
        val back: TextView? = text("‹", 34, false, TEXT)
        back!!.setGravity(Gravity.CENTER)
        back!!.setOnClickListener({ v -> finish() })
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(46)))
        val titles: LinearLayout = LinearLayout(this)
        titles.setOrientation(LinearLayout.VERTICAL)
        titles.setGravity(Gravity.CENTER_VERTICAL)
        val title: TextView? = text("Сеансы и журнал", 18, true, TEXT)
        val sub: TextView? = text("Локальная история Remote", 11, false, MUTED)
        titles.addView(title)
        titles.addView(sub)
        bar.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        val info: TextView? = text("ⓘ", 22, true, TEXT)
        info!!.setGravity(Gravity.CENTER)
        info!!.setContentDescription("Состояние системы")
        info!!.setOnClickListener({ v ->
            startActivity(Intent(this, SystemStatusActivity::class.java))
        })
        bar.addView(info, LinearLayout.LayoutParams(dp(44), dp(46)))
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
        RemoteTheme.applySystemBars(this, palette.dark)
    }

    private fun buildSessions(page: LinearLayout?) {
        val intro: LinearLayout? = card()
        intro!!.addView(text("Сеансы копчения", 18, true, TEXT))
        intro!!.addView(
            detail(
                "Сеанс начинается автоматически по свежей телеметрии. Нажмите карточку, чтобы открыть подробности и график сеанса."
            )
        )
        page!!.addView(intro, margin(8, 4, 8, 4))
        val sessions: List<OperationalHistoryStore.Session?>? = ops!!.querySessions(30)
        if (sessions!!.isEmpty()) {
            val empty: LinearLayout? = card()
            val t: TextView? =
                text(
                    "Сеансов пока нет\nПервая запись появится после получения свежих данных от коптильни.",
                    13,
                    false,
                    MUTED,
                )
            t!!.setGravity(Gravity.CENTER)
            t!!.setPadding(0, dp(14), 0, dp(14))
            empty!!.addView(t)
            page!!.addView(empty, margin(8, 4, 8, 4))
            return
        }
        for (s: OperationalHistoryStore.Session? in sessions!!) page!!.addView(
            sessionCard(s),
            margin(8, 4, 8, 4),
        )
    }

    private fun sessionCard(s: OperationalHistoryStore.Session?): View? {
        val c: LinearLayout? = card()
        c!!.setClickable(true)
        c!!.setOnClickListener({ v -> openSession(s) })
        val test: Boolean = isTestSession(s)
        val header: LinearLayout = LinearLayout(this)
        header.setGravity(Gravity.CENTER_VERTICAL)
        header.addView(text(s!!.title(), 16, true, TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        val chipText: String = if (test) "ТЕСТ" else (if (s!!.active()) "АКТИВЕН" else "ЗАВЕРШЁН")
        val chipColor: Int = if (test) ORANGE else (if (s!!.active()) GREEN else OFF)
        val chip: TextView? = text(chipText, 10, true, ON_ACCENT)
        chip!!.setGravity(Gravity.CENTER)
        chip!!.setPadding(dp(8), dp(4), dp(8), dp(4))
        chip!!.setBackground(round(chipColor, 12))
        header.addView(chip)
        c!!.addView(header)
        if (test) c!!.addView(detail("Локальная симуляция · данные не от коптильни"))
        val end: Long = if (s!!.active()) System.currentTimeMillis() else s!!.endTs
        c!!.addView(
            detail(
                "Длительность · " +
                    duration(Math.max(0, end - s!!.startTs)) +
                    " · точек " +
                    s!!.samples
            )
        )
        c!!.addView(
            detail(
                "Камера " +
                    range(s!!.cameraMin, s!!.cameraMax, " °C") +
                    " · K " +
                    range(s!!.kMin, s!!.kMax, " °C") +
                    " · T " +
                    range(s!!.tMin, s!!.tMax, " °C")
            )
        )
        c!!.addView(
            detail(
                "Макс. ТЭН " +
                    value(s!!.heaterMax, " %") +
                    (if (s!!.outageMs > 0) " · потери связи ≈ " + duration(s!!.outageMs)!! else "")
            )
        )
        val more: TextView? = text("Подробнее о сеансе  ›", 12, true, BLUE)
        more!!.setPadding(0, dp(7), 0, 0)
        more!!.setOnClickListener({ v -> openSession(s) })
        c!!.addView(more)
        val row: LinearLayout = LinearLayout(this)
        row.setGravity(Gravity.CENTER_VERTICAL)
        val csv: Button? = action("Экспорт CSV", BLUE)
        val json: Button? = action("Экспорт JSON", ORANGE)
        csv!!.setOnClickListener({ v -> requestExport(s, "csv") })
        json!!.setOnClickListener({ v -> requestExport(s, "json") })
        val a: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        a.setMargins(0, dp(8), dp(4), 0)
        val b: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        b.setMargins(dp(4), dp(8), 0, 0)
        row.addView(csv, a)
        row.addView(json, b)
        c!!.addView(row)
        return c
    }

    private fun openSession(s: OperationalHistoryStore.Session?) {
        val i: Intent = Intent(this, SessionDetailActivity::class.java)
        i.putExtra("session_id", s!!.id)
        startActivity(i)
    }

    private fun buildEvents(page: LinearLayout?) {
        val c: LinearLayout? = card()
        val head: LinearLayout = LinearLayout(this)
        head.setGravity(Gravity.CENTER_VERTICAL)
        head.addView(
            text("Журнал событий Remote", 18, true, TEXT),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val clear: TextView? = text("Очистить", 11, true, BLUE)
        clear!!.setGravity(Gravity.CENTER)
        clear!!.setPadding(dp(8), dp(5), dp(8), dp(5))
        clear!!.setOnClickListener({ v -> confirmClearJournal() })
        head.addView(clear)
        c!!.addView(head)
        val hs: HorizontalScrollView = HorizontalScrollView(this)
        hs.setHorizontalScrollBarEnabled(false)
        filterBar = LinearLayout(this)
        filterBar!!.setOrientation(LinearLayout.HORIZONTAL)
        filterBar!!.setPadding(0, dp(7), 0, dp(3))
        hs.addView(filterBar, android.widget.FrameLayout.LayoutParams(-2, -2))
        c!!.addView(hs)
        journalRows = LinearLayout(this)
        journalRows!!.setOrientation(LinearLayout.VERTICAL)
        c!!.addView(journalRows)
        rebuildFilterBar()
        renderJournal()
        page!!.addView(c, margin(8, 8, 8, 8))
    }

    private fun rebuildFilterBar() {
        if (filterBar == null) return
        filterBar!!.removeAllViews()
        for (filter: String? in FILTERS!!) {
            val selected: Boolean = filter == eventFilter
            val chip: TextView? = text(filter, 11, true, if (selected) ON_ACCENT else MUTED)
            chip!!.setGravity(Gravity.CENTER)
            chip!!.setPadding(dp(10), dp(6), dp(10), dp(6))
            chip!!.setBackground(round(if (selected) BLUE else FIELD, 13))
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(0, 0, dp(6), 0)
            filterBar!!.addView(chip, lp)
            chip!!.setOnClickListener({ v ->
                eventFilter = filter
                rebuildFilterBar()
                renderJournal()
            })
        }
    }

    private fun renderJournal() {
        if (journalRows == null || ops == null) return
        journalRows!!.removeAllViews()
        val events: List<OperationalHistoryStore.Event?>? = ops!!.queryEvents(500)
        var shown: Int = 0
        val f: SimpleDateFormat = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
        for (e: OperationalHistoryStore.Event? in events!!) {
            if (!matchesFilter(e, eventFilter)) continue
            val row: TextView? =
                text(f.format(Date(e!!.ts)) + " · " + e!!.message, 12, false, eventColor(e!!.type))
            row!!.setPadding(0, dp(5), 0, dp(5))
            journalRows!!.addView(row)
            shown++
            if (shown >= 150) break
        }
        if (shown == 0) journalRows!!.addView(detail("Событий по этому фильтру пока нет"))
    }

    private fun matchesFilter(e: OperationalHistoryStore.Event?, filter: String?): Boolean {
        if ("Все" == filter) return true
        val type: String? = if (e!!.type == null) "" else e!!.type!!.lowercase(Locale.ROOT)
        val msg: String? = if (e!!.message == null) "" else e!!.message!!.lowercase(Locale.ROOT)
        if ("Связь" == filter)
            return "connection" == type || msg!!.contains("связ") || msg!!.contains("mqtt")
        if ("Команды" == filter) return "command" == type
        if ("Auto" == filter) return msg!!.contains("auto") || msg!!.contains("программ")
        if ("Температура" == filter)
            return msg!!.contains("устав") ||
                msg!!.contains("температур") ||
                msg!!.contains("камера достиг")
        if ("Сеансы" == filter) return "session" == type
        if ("TEST" == filter) return "test" == type || msg!!.contains("тест")
        return true
    }

    private fun confirmClearJournal() {
        AlertDialog.Builder(this, RemoteTheme.dialogTheme(this))
            .setTitle("Очистить журнал?")!!
            .setMessage("Сеансы и их сводки останутся. Будут удалены только события Remote.")!!
            .setPositiveButton(
                "Очистить",
                { d, w ->
                    ops!!.clearEvents()
                    renderJournal()
                    Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT)!!.show()
                },
            )!!
            .setNegativeButton("Отмена", null)!!
            .show()
    }

    private fun eventColor(type: String?): Int {
        if ("error" == type) return palette.red
        if ("connection" == type || "test" == type) return ORANGE
        if ("command" == type) return BLUE
        if ("session" == type) return GREEN
        return TEXT
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

    private fun requestExport(session: OperationalHistoryStore.Session?, format: String?) {
        exportSession = session
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
        if (
            resultCode != Activity.RESULT_OK ||
                data == null ||
                data!!.getData() == null ||
                exportSession == null
        )
            return
        try {
            writeExport(
                data!!.getData(),
                exportSession,
                if (requestCode == REQ_CSV) "csv" else "json",
            )
            Toast.makeText(this, "Экспорт сохранён", Toast.LENGTH_SHORT)!!.show()
            ops!!.addEvent(
                System.currentTimeMillis(),
                "info",
                "Экспортирован сеанс · " + (if (requestCode == REQ_CSV) "CSV" else "JSON"),
            )
            renderJournal()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка экспорта: " + safe(e)!!, Toast.LENGTH_LONG)!!.show()
        }
    }

    @Throws(Exception::class)
    private fun writeExport(uri: Uri?, s: OperationalHistoryStore.Session?, format: String?) {
        val end: Long = if (s!!.active()) System.currentTimeMillis() else s!!.endTs
        val samples: List<TelemetryHistoryStore.Sample?>? = telemetry!!.query(s!!.startTs, end, 0)
        val os: OutputStream? = getContentResolver()!!.openOutputStream(uri!!)
        if (os == null) throw IllegalStateException("Не удалось открыть файл")
        OutputStreamWriter(os, "UTF-8")
            .use({ w ->
                if ("csv" == format) writeCsv(w, s, samples) else writeJson(w, s, samples)
            })
    }

    @Throws(Exception::class)
    private fun writeCsv(
        w: OutputStreamWriter?,
        s: OperationalHistoryStore.Session?,
        samples: List<TelemetryHistoryStore.Sample?>?,
    ) {
        val iso: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        w!!.write("source," + (if (isTestSession(s)) "test" else "live") + "\n")
        w!!.write("session_start," + iso.format(Date(s!!.startTs)) + "\n")
        w!!.write("session_end," + iso.format(Date(s!!.effectiveEnd())) + "\n")
        w!!.write("duration_minutes," + (s!!.durationMs() / 60000L) + "\n")
        w!!.write("outage_seconds," + (s!!.outageMs / 1000L) + "\n\n")
        w!!.write("timestamp,camera_c,setpoint_c,probe_k_c,probe_t_c,heater_pct\n")
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
        s: OperationalHistoryStore.Session?,
        samples: List<TelemetryHistoryStore.Sample?>?,
    ) {
        val root: JSONObject = JSONObject()
        val summary: JSONObject = JSONObject()
        summary.put("source", if (isTestSession(s)) "test" else "live")
        summary.put("start_ts", s!!.startTs)
        summary.put("end_ts", s!!.effectiveEnd())
        summary.put("duration_ms", s!!.durationMs())
        summary.put("samples", s!!.samples)
        summary.put("outage_ms", s!!.outageMs)
        put(summary, "camera_min", s!!.cameraMin)
        put(summary, "camera_max", s!!.cameraMax)
        put(summary, "probe_k_min", s!!.kMin)
        put(summary, "probe_k_max", s!!.kMax)
        put(summary, "probe_t_min", s!!.tMin)
        put(summary, "probe_t_max", s!!.tMax)
        put(summary, "heater_max", s!!.heaterMax)
        root.put("session", summary)
        val a: JSONArray = JSONArray()
        for (x: TelemetryHistoryStore.Sample? in samples!!) {
            val o: JSONObject = JSONObject()
            o.put("ts", x!!.ts)
            put(o, "camera", x!!.camera)
            put(o, "setpoint", x!!.setpoint)
            put(o, "probe_k", x!!.probeK)
            put(o, "probe_t", x!!.probeT)
            put(o, "heater", x!!.heater)
            a.put(o)
        }
        root.put("telemetry", a)
        w!!.write(root.toString(2))
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
        b.setTextColor(ON_ACCENT)
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

    private val NAVY: Int get() = palette.topBar
    private val BG: Int get() = palette.background
    private val CARD: Int get() = palette.surface
    private val TEXT: Int get() = palette.ink
    private val MUTED: Int get() = palette.muted
    private val BORDER: Int get() = palette.outline
    private val GREEN: Int get() = palette.green
    private val BLUE: Int get() = palette.primary
    private val ORANGE: Int get() = palette.orange
    private val OFF: Int get() = palette.off
    private val FIELD: Int get() = palette.surfaceVariant
    private val ON_ACCENT: Int get() = if (palette.dark) Color.rgb(31, 25, 22) else Color.WHITE

    companion object {
        private val REQ_CSV: Int = 4101
        private val REQ_JSON: Int = 4102
        private val FILTERS: Array<String> =
            arrayOf<String>("Все", "Связь", "Команды", "Auto", "Температура", "Сеансы", "TEST")

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

        private fun range(min: Double, max: Double, suffix: String?): String? {
            if (java.lang.Double.isNaN(min) || java.lang.Double.isNaN(max)) return "—"
            return one(min) + "…" + one(max) + suffix
        }

        private fun value(v: Double, suffix: String?): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) "—"
            else one(v)!! + suffix!!
        }

        private fun one(v: Double): String? {
            return String.format(Locale.getDefault(), "%.1f", v)
        }

        private fun duration(ms: Long): String? {
            val m: Long = Math.max(0, ms / 60000L)
            val h: Long = m / 60
            val r: Long = m % 60
            return if (h > 0) h.toString() + " ч " + r.toString() + " мин"
            else m.toString() + " мин"
        }

        private fun safe(t: Throwable?): String? {
            val s: String? = if (t == null) "" else t!!.message
            return if (s == null || s!!.trim({ it <= ' ' }).isEmpty()) t!!.javaClass.getSimpleName()
            else s
        }
    }
}
