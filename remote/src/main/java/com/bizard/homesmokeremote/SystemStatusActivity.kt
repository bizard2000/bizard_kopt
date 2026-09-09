package com.bizard.homesmokeremote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Read-only diagnostics assembled from Remote runtime state and local stores. */
class SystemStatusActivity : Activity() {
    private var prefs: SharedPreferences? = null
    private var telemetry: TelemetryHistoryStore? = null
    private var ops: OperationalHistoryStore? = null
    private var connectionBody: TextView? = null
    private var telemetryBody: TextView? = null
    private var historyBody: TextView? = null
    private var notificationsBody: TextView? = null
    private var remoteBody: TextView? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val tick: Runnable =
        object : Runnable {
            public override fun run() {
                refresh()
                handler.postDelayed(this, 1000L)
            }
        }

    protected override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        telemetry = TelemetryHistoryStore(this)
        ops = OperationalHistoryStore(this)
        val root: View? = buildRoot()
        setContentView(root)
        applyInsets(root)
        refresh()
        handler.postDelayed(tick, 1000L)
    }

    protected override fun onDestroy() {
        handler.removeCallbacks(tick)
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
        val intro: LinearLayout? = card()
        intro!!.addView(text("Состояние системы", 18, true, TEXT))
        intro!!.addView(
            detail("Только диагностика Remote. Здесь нет новых команд или полей протокола.")
        )
        page.addView(intro, margin(8, 4, 8, 4))
        connectionBody = addStatusCard(page, "Связь")
        telemetryBody = addStatusCard(page, "Телеметрия")
        historyBody = addStatusCard(page, "Локальная история")
        notificationsBody = addStatusCard(page, "Уведомления")
        remoteBody = addStatusCard(page, "Remote")
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
        titles.addView(text("Состояние системы", 18, true, TEXT))
        titles.addView(text("Диагностика HomeSmoke Remote", 11, false, MUTED))
        bar.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        return bar
    }

    private fun addStatusCard(page: LinearLayout?, title: String?): TextView? {
        val c: LinearLayout? = card()
        c!!.addView(text(title, 18, true, TEXT))
        val body: TextView? = detail("Обновление…")
        body!!.setTextColor(TEXT)
        body!!.setPadding(0, dp(7), 0, 0)
        c!!.addView(body)
        page!!.addView(c, margin(8, 4, 8, 4))
        return body
    }

    private fun refresh() {
        if (prefs == null || telemetry == null || ops == null) return
        val now: Long = System.currentTimeMillis()
        val diagAt: Long = prefs!!.getLong("diag_updated_at", 0L)
        val diagAge: Long = if (diagAt > 0) Math.max(0, now - diagAt) else java.lang.Long.MAX_VALUE
        val diagFresh: Boolean = diagAge <= 5000L
        val test: Boolean =
            prefs!!.getBoolean("test_mode_active", false) ||
                prefs!!.getBoolean("diag_test_running", false)
        val mqttConnected: Boolean = diagFresh && prefs!!.getBoolean("diag_mqtt_connected", false)
        val mqttConnecting: Boolean = diagFresh && prefs!!.getBoolean("diag_mqtt_connecting", false)
        val mqtt: String =
            if (mqttConnected) "Подключён"
            else
                (if (mqttConnecting) "Подключение…"
                else (if (diagFresh) "Отключён" else "Нет актуального снимка"))
        val runtime: String? =
            if (diagAt > 0)
                "Диагностика обновлена · " +
                    (if (diagAge < 2000L) "сейчас" else age(diagAt, now)!! + " назад")
            else "Диагностика ещё не обновлялась"
        connectionBody!!.setText(
            "MQTT · " +
                mqtt +
                "\nИсточник данных · " +
                (if (test) "ТЕСТ · локальная симуляция" else "Коптильня / MQTT") +
                "\n" +
                runtime
        )
        connectionBody!!.setTextColor(
            if (mqttConnected) GREEN
            else (if (test) ORANGE else (if (mqttConnecting) ORANGE else MUTED))
        )

        val latest: Long = telemetry!!.latestTimestamp()
        val live: Boolean = latest > 0 && Math.abs(now - latest) <= LIVE_MS
        val latestTest: Boolean = latest > 0 && isTestTimestamp(latest)
        val device: String? = prefs!!.getString("diag_device_id", "—")
        val mode: String? = prefs!!.getString("diag_mode_display", "—")
        val auto: String? = prefs!!.getString("diag_auto_display", "—")
        val heater: String? = prefs!!.getString("diag_heater_display", "—")
        telemetryBody!!.setText(
            "Последняя точка · " +
                (if (latest > 0) dateTime(latest) + " · " + age(latest, now) + " назад"
                else "нет данных") +
                "\nСвежесть · " +
                (if (live) "LIVE" else "не свежая") +
                " · " +
                (if (latestTest) "тестовая запись" else "обычные данные") +
                "\nУстройство · " +
                empty(device) +
                "\nРежим · " +
                empty(mode) +
                " · Auto " +
                empty(auto) +
                "\nТЭН · " +
                empty(heater)
        )
        telemetryBody!!.setTextColor(if (live) (if (latestTest) ORANGE else GREEN) else MUTED)

        val points: Int = telemetry!!.countSamples(now - HISTORY_MS, now)
        val sessions: List<OperationalHistoryStore.Session?>? = ops!!.querySessions(1000)
        val events: List<OperationalHistoryStore.Event?>? = ops!!.queryEvents(500)
        val last: OperationalHistoryStore.Session? =
            if (sessions!!.isEmpty()) null else sessions!!.get(0)
        val lastSession: String? =
            if (last == null) "нет"
            else
                last!!.title() +
                    " · " +
                    (if (last!!.active()) "активен" else duration(last!!.durationMs()))
        historyBody!!.setText(
            "Точки телеметрии за 24 ч · " +
                points +
                "\nСеансов сохранено · " +
                sessions!!.size +
                "\nСобытий в журнале · " +
                events!!.size +
                " / 500\nПоследний сеанс · " +
                lastSession +
                "\nСырые точки хранятся до 24 ч; сводки сеансов сохраняются отдельно."
        )
        historyBody!!.setTextColor(TEXT)

        val nConnection: Boolean = prefs!!.getBoolean("notify_connection", true)
        val nSet: Boolean = prefs!!.getBoolean("notify_setpoint", true)
        val nSession: Boolean = prefs!!.getBoolean("notify_session", false)
        val permission: String?
        if (Build.VERSION.SDK_INT >= 33)
            permission =
                if (
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                )
                    "разрешены системой"
                else "нет системного разрешения"
        else permission = "разрешение системы не требуется"
        notificationsBody!!.setText(
            "Связь · " +
                onOff(nConnection) +
                "\nКамера достигла уставки · " +
                onOff(nSet) +
                "\nНачало/завершение сеанса · " +
                onOff(nSession) +
                "\nAndroid · " +
                permission
        )
        notificationsBody!!.setTextColor(if (permission!!.startsWith("нет")) ORANGE else TEXT)

        val command: String? = prefs!!.getString("diag_command_display", "—")
        val testName: String? = prefs!!.getString("test_active_name", "")
        remoteBody!!.setText(
            "Версия · " +
                versionName() +
                "\nТестовый режим · " +
                (if (test) ("активен" + (if (testName!!.isEmpty()) "" else " · " + testName!!))
                else "выключен") +
                "\nПоследняя команда/статус · " +
                empty(command) +
                "\nAndroid · " +
                Build.VERSION.RELEASE +
                " (API " +
                Build.VERSION.SDK_INT +
                ")"
        )
        remoteBody!!.setTextColor(if (test) ORANGE else TEXT)
    }

    private fun isTestTimestamp(ts: Long): Boolean {
        if (prefs!!.getBoolean("test_mode_active", false)) {
            val start: Long = prefs!!.getLong("test_active_start", 0L)
            if (start > 0 && ts >= start) return true
        }
        try {
            val a: JSONArray = JSONArray(prefs!!.getString("test_intervals", "[]"))
            for (i: Int in 0 until a.length()) {
                val x: JSONObject? = a.optJSONObject(i)
                if (x == null) continue
                val s: Long = x!!.optLong("start", 0L)
                val e: Long = x!!.optLong("end", 0L)
                if (s > 0 && e >= s && ts >= s && ts <= e) return true
            }
        } catch (ignored: Exception) {}

        return false
    }

    private fun versionName(): String? {
        try {
            return getPackageManager()!!.getPackageInfo(getPackageName(), 0)!!.versionName
        } catch (ignored: Exception) {
            return "—"
        }
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
        t!!.setLineSpacing(0f, 1.08f)
        return t
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
        private val ORANGE: Int = Color.rgb(197, 101, 16)
        private val LIVE_MS: Long = 10000L
        private val HISTORY_MS: Long = 24L * 60L * 60L * 1000L

        private fun onOff(v: Boolean): String? {
            return if (v) "включено" else "выключено"
        }

        private fun empty(v: String?): String? {
            return if (v == null || v!!.trim({ it <= ' ' }).isEmpty()) "—"
            else v!!.trim({ it <= ' ' })
        }

        private fun dateTime(ts: Long): String? {
            return SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(ts))
        }

        private fun age(ts: Long, now: Long): String? {
            val sec: Long = Math.max(0, (now - ts) / 1000L)
            if (sec < 60) return sec.toString() + " сек"
            val min: Long = sec / 60
            if (min < 60) return min.toString() + " мин"
            val h: Long = min / 60
            if (h < 24) return h.toString() + " ч " + (min % 60) + " мин"
            return (h / 24).toString() + " д " + (h % 24) + " ч"
        }

        private fun duration(ms: Long): String? {
            val m: Long = Math.max(0, ms / 60000L)
            val h: Long = m / 60
            val r: Long = m % 60
            return if (h > 0) h.toString() + " ч " + r.toString() + " мин"
            else m.toString() + " мин"
        }
    }
}
