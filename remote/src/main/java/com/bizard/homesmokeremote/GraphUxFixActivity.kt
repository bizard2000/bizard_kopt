package com.bizard.homesmokeremote

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Presentation/runtime refinements; MQTT and Arduino protocol are untouched. */
class GraphUxFixActivity : GraphUxActivity() {
    private val fixHandler: Handler = Handler(Looper.getMainLooper())
    private var scenarioSpinner: Spinner? = null
    private var scenarioButton: Button? = null
    private var autoStopIssued: Boolean = false
    private var previousTestRunning: Boolean = false
    private var testPresentationApplied: Boolean = false
    private var fixHistory: TelemetryHistoryStore? = null
    private var lastDiagnosticWriteAt: Long = 0L

    private val fixTick: Runnable =
        object : Runnable {
            public override fun run() {
                installScenarioChooser()
                refreshScenarioChooser()
                syncTestSessionBoundary()
                syncLatestGraphSession()
                fixGraphHeader()
                fixGraphSummary()
                fixTestStatus()
                fixModeChipText()
                syncDiagnosticSnapshot()
                enforceSinglePassScenario()
                fixHandler.postDelayed(this, 350L)
            }
        }
    private val isTestRunning: Boolean
        get() {
            return readGraphBoolean("testRunning", false)
        }

    protected override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preEnhanceGraphPage()
        fixHistory = TelemetryHistoryStore(this)
        previousTestRunning = isTestRunning
        fixHandler.postDelayed(fixTick, 250L)
    }

    protected override fun onDestroy() {
        fixHandler.removeCallbacks(fixTick)
        if (fixHistory != null) fixHistory!!.close()
        super.onDestroy()
    }

    /**
     * Prepare the graph page while it is still off-screen, removing the one-frame legacy title
     * flash.
     */
    private fun preEnhanceGraphPage() {
        val page: Any? = readMainObject("graphPage")
        if (page is View)
            invokeGraph("enhanceAttachedPage", arrayOf<Class<*>?>(View::class.java), page)
    }

    private fun installScenarioChooser() {
        if (scenarioButton != null && scenarioButton!!.getParent() != null) return
        val content: View? = findViewById<View?>(android.R.id.content)
        val spinner: Spinner? = findSpinner(content)
        if (spinner == null || !(spinner!!.getParent() is ViewGroup)) return
        scenarioSpinner = spinner
        val parent: ViewGroup? = spinner!!.getParent() as ViewGroup?
        val index: Int = parent!!.indexOfChild(spinner)
        var original: ViewGroup.LayoutParams? = spinner!!.getLayoutParams()
        parent!!.removeView(spinner)
        scenarioButton = Button(this)
        scenarioButton!!.setAllCaps(false)
        scenarioButton!!.setTextSize(15f)
        scenarioButton!!.setTypeface(Typeface.DEFAULT)
        scenarioButton!!.setTextColor(TEXT)
        scenarioButton!!.setGravity(Gravity.CENTER_VERTICAL or Gravity.START)
        scenarioButton!!.setPadding(dp(12), 0, dp(12), 0)
        scenarioButton!!.setMinWidth(0)
        scenarioButton!!.setMinimumWidth(0)
        scenarioButton!!.setMinHeight(0)
        scenarioButton!!.setMinimumHeight(0)
        if (Build.VERSION.SDK_INT >= 21) {
            scenarioButton!!.setStateListAnimator(null)
            scenarioButton!!.setBackgroundTintList(null)
        }
        scenarioButton!!.setBackground(roundStroke(FIELD, 13, BORDER, 1))
        scenarioButton!!.setOnClickListener({ v -> showScenarioDialog() })
        updateScenarioButtonText()
        if (original == null) original = ViewGroup.LayoutParams(-1, dp(46))
        parent!!.addView(scenarioButton, Math.max(0, index), original)
    }

    private fun showScenarioDialog() {
        if (scenarioSpinner == null || isTestRunning) return
        val selected: Int =
            Math.max(
                0,
                Math.min(TEST_SCENARIOS!!.size - 1, scenarioSpinner!!.getSelectedItemPosition()),
            )
        AlertDialog.Builder(this, RemoteTheme.dialogTheme(this))
            .setTitle("Сценарий теста")!!
            .setSingleChoiceItems(
                TEST_SCENARIOS,
                selected,
                { dialog, which ->
                    scenarioSpinner!!.setSelection(which)
                    updateScenarioButtonText()
                    dialog!!.dismiss()
                },
            )!!
            .setNegativeButton("Отмена", null)!!
            .show()
    }

    private fun refreshScenarioChooser() {
        if (scenarioButton == null) return
        val running: Boolean = isTestRunning
        scenarioButton!!.setEnabled(!running)
        scenarioButton!!.setAlpha(if (running) .55f else 1f)
        updateScenarioButtonText()
    }

    private fun updateScenarioButtonText() {
        if (scenarioButton == null) return
        var selected: Int =
            if (scenarioSpinner == null) readGraphInt("testScenarioIndex", 0)
            else scenarioSpinner!!.getSelectedItemPosition()
        selected = Math.max(0, Math.min(TEST_SCENARIOS!!.size - 1, selected))
        scenarioButton!!.setText(TEST_SCENARIOS!![selected] + "   ▾")
    }

    private fun syncTestSessionBoundary() {
        val running: Boolean = isTestRunning
        if (running && !previousTestRunning) {
            val started: Long = readGraphLong("testStartedAt", System.currentTimeMillis())
            if (fixHistory != null) fixHistory!!.markSessionBoundary(started)
            writeMainBoolean("graphSessionMode", true)
            writeMainLong("graphSessionStartAt", started)
            invokeMain("refreshGraph", arrayOfNulls<Class<*>?>(0))
        }
        previousTestRunning = running
    }

    private fun syncLatestGraphSession() {
        if (
            fixHistory == null ||
                !readMainBoolean("graphVisible", false) ||
                !readMainBoolean("graphSessionMode", false)
        )
            return
        val latest: Long = fixHistory!!.latestTimestamp()
        if (latest <= 0) return
        val start: Long =
            if (isTestRunning) readGraphLong("testStartedAt", latest)
            else fixHistory!!.latestSessionStart(latest, SESSION_SPLIT_MS)
        val current: Long = readMainLong("graphSessionStartAt", 0L)
        if (start > 0 && Math.abs(start - current) > 1000L) {
            writeMainLong("graphSessionStartAt", start)
            invokeMain("refreshGraph", arrayOfNulls<Class<*>?>(0))
        }
    }

    private fun fixGraphSummary() {
        if (
            fixHistory == null ||
                !readMainBoolean("graphVisible", false) ||
                readMainBoolean("graphSessionMode", false)
        )
            return
        val raw: Any? = readMainObject("graphSummary")
        if (!(raw is TextView)) return
        val summary: TextView? = raw as TextView?
        val current: String? = (summary!!.getText()).toString()
        if (
            current!!.isEmpty() ||
                current!!.contains(" · сеансов:") ||
                current!!.contains("данных") && current!!.contains("нет")
        )
            return
        val window: Long = readMainLong("graphWindowMs", 3L * 60L * 60L * 1000L)
        val sessions: Int =
            fixHistory!!.countSessions(
                System.currentTimeMillis() - window,
                System.currentTimeMillis(),
                SESSION_SPLIT_MS,
            )
        if (sessions > 1) summary!!.setText(current + " · сеансов: " + sessions)
    }

    private fun fixGraphHeader() {
        val title: TextView? =
            findVisibleExact(findViewById<View?>(android.R.id.content), "График температуры")
        if (title != null) title!!.setText("График")
    }

    private fun fixTestStatus() {
        val content: View? = findViewById<View?>(android.R.id.content)
        val running: Boolean = isTestRunning
        val status: TextView? =
            findVisibleAny(content, "ОФЛАЙН", "ГОТОВО", "СТАРЫЕ ДАННЫЕ", "НЕТ ДАННЫХ", "ТЕСТ")
        val availability: TextView? = findVisibleStartsWith(content, "Управление недоступно")
        val device: TextView? = asText(readMainObject("deviceState"))
        val deviceBadge: TextView? = asText(readMainObject("deviceBadge"))
        val deviceDot: TextView? = asText(readMainObject("deviceDot"))
        val deviceDetail: TextView? = asText(readMainObject("deviceDetail"))
        if (running) {
            if (status != null) {
                status!!.setText("ТЕСТ")
                status!!.setTextColor(Color.WHITE)
                status!!.setBackground(round(ORANGE, 12))
            }
            if (deviceBadge != null) {
                deviceBadge!!.setText("TEST")
                deviceBadge!!.setTextColor(Color.WHITE)
                deviceBadge!!.setBackground(round(ORANGE, 14))
                deviceBadge!!.setContentDescription("Тестовый режим · локальная симуляция")
            }
            if (deviceDot != null) deviceDot!!.setTextColor(ORANGE)
            if (device != null) {
                device!!.setText("Тестовые данные")
                device!!.setTextColor(ORANGE)
            }
            if (deviceDetail != null) {
                deviceDetail!!.setText("Локальный симулятор · не реальная телеметрия")
                deviceDetail!!.setTextColor(MUTED)
            }
            if (availability != null) {
                availability!!.setText("Управление недоступно · тестовый режим")
                availability!!.setTextColor(ORANGE_TEXT)
                availability!!.setBackground(roundStroke(WARN_BG, 10, ORANGE, 1))
            }
            testPresentationApplied = true
            return
        }
        if (
            testPresentationApplied ||
                (deviceBadge != null && "TEST".contentEquals(deviceBadge!!.getText()))
        ) {
            if (deviceBadge != null) {
                deviceBadge!!.setText("SMOKE")
                deviceBadge!!.setContentDescription("Состояние коптильни")
            }
            invokeMain(
                "setDeviceUi",
                arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType, String::class.java),
                false,
                "Тест завершён · ожидаются реальные данные",
            )
            testPresentationApplied = false
        }
        if (status != null && "ТЕСТ".contentEquals(status!!.getText())) {
            status!!.setText("ОФЛАЙН")
            status!!.setTextColor(Color.WHITE)
            status!!.setBackground(round(OFF, 12))
        }
    }

    private fun syncDiagnosticSnapshot() {
        val now: Long = System.currentTimeMillis()
        if (now - lastDiagnosticWriteAt < 1000L) return
        lastDiagnosticWriteAt = now
        val mqtt: Any? = readMainObject("mqtt")
        var connected: Boolean = false
        if (mqtt != null) {
            try {
                val m: Method? = mqtt!!.javaClass.getDeclaredMethod("isConnected")
                m!!.setAccessible(true)
                connected = java.lang.Boolean.TRUE == m!!.invoke(mqtt)
            } catch (ignored: Exception) {}
        }
        val mode: TextView? = asText(readMainObject("mode"))
        val autoChip: TextView? = asText(readMainObject("autoChip"))
        val power: TextView? = asText(readMainObject("power"))
        val command: TextView? = asText(readMainObject("lastCommand"))
        val deviceId: Any? = readMainObject("deviceId")
        val e: SharedPreferences.Editor? =
            getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)!!.edit()
        e!!
            .putLong("diag_updated_at", now)!!
            .putBoolean("diag_mqtt_connected", connected)!!
            .putBoolean("diag_mqtt_connecting", readMainBoolean("connecting", false))!!
            .putBoolean("diag_want_connection", readMainBoolean("wantConnection", false))!!
            .putBoolean("diag_test_running", isTestRunning)!!
            .putLong("diag_last_telemetry_at", readMainLong("lastTelemetryAt", 0L))!!
            .putString("diag_device_id", if (deviceId == null) "—" else (deviceId).toString())!!
            .putString(
                "diag_mode_display",
                if (mode == null) "—" else (mode!!.getText()).toString(),
            )!!
            .putString(
                "diag_auto_display",
                if (autoChip == null) "—" else (autoChip!!.getText()).toString(),
            )!!
            .putString(
                "diag_heater_display",
                if (power == null) "—" else (power!!.getText()).toString(),
            )!!
            .putString(
                "diag_command_display",
                if (command == null) "—" else (command!!.getText()).toString(),
            )!!
            .apply()
    }

    private fun fixModeChipText() {
        forceModeChipText(findViewById<View?>(android.R.id.content))
    }

    private fun forceModeChipText(root: View?) {
        if (root is TextView && root!!.getVisibility() == View.VISIBLE) {
            val s: String? = ((root as TextView).getText()).toString().trim({ it <= ' ' })
            if ("● PID" == s || "● AUTO" == s || "● Ручной" == s || "● STOP" == s)
                (root as TextView).setTextColor(Color.WHITE)
        }
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) forceModeChipText(g!!.getChildAt(i))
        }
    }

    private fun enforceSinglePassScenario() {
        if (!isTestRunning) {
            autoStopIssued = false
            return
        }
        val started: Long = readGraphLong("testStartedAt", 0L)
        val scenario: Int =
            Math.max(
                0,
                Math.min(TEST_DURATIONS_MS!!.size - 1, readGraphInt("testScenarioIndex", 0)),
            )
        if (
            started <= 0 ||
                System.currentTimeMillis() - started < TEST_DURATIONS_MS!![scenario] ||
                autoStopIssued
        )
            return
        autoStopIssued = true
        val name: String? = TEST_SCENARIOS!![scenario]
        invokeGraph("stopTestScenario", arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType), true)
        Toast.makeText(this, "Тест «" + name + "» завершён", Toast.LENGTH_SHORT)!!.show()
    }

    private fun findSpinner(root: View?): Spinner? {
        if (root is Spinner && root!!.getVisibility() == View.VISIBLE) return root as Spinner?
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val x: Spinner? = findSpinner(g!!.getChildAt(i))
                if (x != null) return x
            }
        }
        return null
    }

    private fun findVisibleExact(root: View?, exact: String?): TextView? {
        if (
            root is TextView &&
                root!!.getVisibility() == View.VISIBLE &&
                exact!!.contentEquals((root as TextView).getText())
        )
            return root as TextView?
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val x: TextView? = findVisibleExact(g!!.getChildAt(i), exact)
                if (x != null) return x
            }
        }
        return null
    }

    private fun findVisibleStartsWith(root: View?, prefix: String?): TextView? {
        if (root is TextView && root!!.getVisibility() == View.VISIBLE) {
            val s: CharSequence? = (root as TextView).getText()
            if (s != null && s!!.toString()!!.startsWith(prefix!!)) return root as TextView?
        }
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val x: TextView? = findVisibleStartsWith(g!!.getChildAt(i), prefix)
                if (x != null) return x
            }
        }
        return null
    }

    private fun findVisibleAny(root: View?, vararg values: String?): TextView? {
        if (root is TextView && root!!.getVisibility() == View.VISIBLE) {
            val s: String? = ((root as TextView).getText()).toString()
            for (value: String? in values!!) if (value == s) return root as TextView?
        }
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val x: TextView? = findVisibleAny(g!!.getChildAt(i), *values)
                if (x != null) return x
            }
        }
        return null
    }

    private fun invokeGraph(name: String?, types: Array<Class<*>?>?, vararg args: Any?) {
        try {
            val m: Method? = GraphUxActivity::class.java!!.getDeclaredMethod(name, *types!!)
            m!!.setAccessible(true)
            m!!.invoke(this, *args)
        } catch (ignored: Exception) {}
    }

    private fun invokeMain(name: String?, types: Array<Class<*>?>?, vararg args: Any?) {
        try {
            val m: Method? = MainActivity::class.java!!.getDeclaredMethod(name, *types!!)
            m!!.setAccessible(true)
            m!!.invoke(this, *args)
        } catch (ignored: Exception) {}
    }

    private fun readGraphBoolean(name: String?, def: Boolean): Boolean {
        try {
            val f: Field? = GraphUxActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.getBoolean(this)
        } catch (e: Exception) {
            return def
        }
    }

    private fun readGraphInt(name: String?, def: Int): Int {
        try {
            val f: Field? = GraphUxActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.getInt(this)
        } catch (e: Exception) {
            return def
        }
    }

    private fun readGraphLong(name: String?, def: Long): Long {
        try {
            val f: Field? = GraphUxActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.getLong(this)
        } catch (e: Exception) {
            return def
        }
    }

    private fun readMainBoolean(name: String?, def: Boolean): Boolean {
        try {
            val f: Field? = MainActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.getBoolean(this)
        } catch (e: Exception) {
            return def
        }
    }

    private fun readMainLong(name: String?, def: Long): Long {
        try {
            val f: Field? = MainActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.getLong(this)
        } catch (e: Exception) {
            return def
        }
    }

    private fun readMainObject(name: String?): Any? {
        try {
            val f: Field? = MainActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            return f!!.get(this)
        } catch (e: Exception) {
            return null
        }
    }

    private fun writeMainBoolean(name: String?, value: Boolean) {
        try {
            val f: Field? = MainActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            f!!.setBoolean(this, value)
        } catch (ignored: Exception) {}
    }

    private fun writeMainLong(name: String?, value: Long) {
        try {
            val f: Field? = MainActivity::class.java!!.getDeclaredField(name)
            f!!.setAccessible(true)
            f!!.setLong(this, value)
        } catch (ignored: Exception) {}
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

    private fun dp(value: Int): Int {
        return Math.round(value * getResources()!!.getDisplayMetrics()!!.density)
    }

    companion object {
        private val TEXT: Int = Color.rgb(21, 31, 47)
        private val MUTED: Int = Color.rgb(101, 116, 139)
        private val BORDER: Int = Color.rgb(220, 225, 232)
        private val ORANGE: Int = Color.rgb(231, 138, 7)
        private val ORANGE_TEXT: Int = Color.rgb(151, 88, 0)
        private val OFF: Int = Color.rgb(116, 129, 145)
        private val FIELD: Int = Color.rgb(250, 251, 252)
        private val WARN_BG: Int = Color.rgb(255, 247, 232)
        private val SESSION_SPLIT_MS: Long = 10L * 60L * 1000L
        private val TEST_SCENARIOS: Array<String> =
            arrayOf<String>(
                "Полный цикл",
                "Нагрев камеры",
                "Стабилизация PID",
                "Auto-программа",
                "Щуп достигает цели",
                "Потеря и восстановление связи",
            )
        private val TEST_DURATIONS_MS: LongArray? =
            longArrayOf(178000L, 100000L, 118000L, 118000L, 100000L, 52000L)

        private fun asText(value: Any?): TextView? {
            return if (value is TextView) value as TextView? else null
        }
    }
}
