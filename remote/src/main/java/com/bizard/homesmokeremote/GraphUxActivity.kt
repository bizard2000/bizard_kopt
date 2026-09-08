package com.bizard.homesmokeremote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote UI refinement plus local operational features: sessions, notifications, event journal,
 * export and a strictly local telemetry simulator. MQTT/Arduino protocol is untouched.
 */
open class GraphUxActivity : MainActivity() {

    private var uxHistory: TelemetryHistoryStore? = null
    private var operational: OperationalHistoryStore? = null
    private var remotePrefs: SharedPreferences? = null
    private var host: ViewGroup? = null
    private var graphEnhanced: Boolean = false
    private var notificationCardAdded: Boolean = false
    private var testCardAdded: Boolean = false
    private var historyButtonAdded: Boolean = false
    private var graphPage: ViewGroup? = null
    private var graphChartView: TemperatureChartView? = null
    private var graphRecordStatus: TextView? = null
    private var graphLiveValues: TextView? = null
    private var graphEmptyState: TextView? = null
    private var graphHeaterScale: TextView? = null
    private var graphBaseSummary: TextView? = null
    private var graphPointCard: View? = null
    private var liveStateKnown: Boolean = false
    private var lastLiveState: Boolean = false
    private var setpointStateKnown: Boolean = false
    private var withinSetpoint: Boolean = false
    private var lastObservedTarget: Double = java.lang.Double.NaN

    private var testScenarioSpinner: Spinner? = null
    private var testStartButton: Button? = null
    private var testStopButton: Button? = null
    private var testStatus: TextView? = null
    private var testBannerTitle: TextView? = null
    private var testBannerDetail: TextView? = null
    private var testBanner: View? = null
    private var testRunning: Boolean = false
    private var previousWantConnection: Boolean = false
    private var testScenarioIndex: Int = 0
    private var testStartedAt: Long = 0L

    private val graphUxHandler: Handler = Handler(Looper.getMainLooper())

    private val graphUxTick: Runnable =
        object : Runnable {
            public override fun run() {
                refreshOperationalFeatures()
                refreshGraphUx()
                refreshTestUi()
                updateVersionLabels()
                graphUxHandler.postDelayed(this, 2500L)
            }
        }

    private val simulationTick: Runnable =
        object : Runnable {
            public override fun run() {
                if (!testRunning) return
                val now: Long = System.currentTimeMillis()
                val elapsed: Double = (now - testStartedAt) / 1000.0
                if (shouldEmitTestFrame(testScenarioIndex, elapsed))
                    injectTestFrame(buildTestFrame(testScenarioIndex, elapsed), now)
                refreshTestUi()
                graphUxHandler.postDelayed(this, 2000L)
            }
        }

    protected override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        remotePrefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        uxHistory = TelemetryHistoryStore(this)
        operational = OperationalHistoryStore(this)
        recoverInterruptedTestInterval()
        createNotificationChannel()
        installHostWatcher()
        installHistoryButton()
        updateVersionLabels()
        graphUxHandler.postDelayed({ ensureNotificationPermission(false) }, 1200L)
        graphUxHandler.postDelayed(graphUxTick, 700L)
    }

    protected override fun onDestroy() {
        if (testRunning) stopTestScenario(false)
        graphUxHandler.removeCallbacks(graphUxTick)
        graphUxHandler.removeCallbacks(simulationTick)
        if (uxHistory != null) uxHistory!!.close()
        if (operational != null) operational!!.close()
        super.onDestroy()
    }

    private fun installHostWatcher() {
        val content: View? = findViewById<View?>(android.R.id.content)
        if (!(content is ViewGroup)) return
        val contentGroup: ViewGroup? = content as ViewGroup?
        if (contentGroup!!.getChildCount() == 0) return
        val root: View? = contentGroup!!.getChildAt(0)
        if (!(root is ViewGroup)) return
        val rootGroup: ViewGroup? = root as ViewGroup?
        if (rootGroup!!.getChildCount() < 2) return
        val candidate: View? = rootGroup!!.getChildAt(1)
        if (!(candidate is ViewGroup)) return
        host = candidate as ViewGroup?
        host!!.setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                public override fun onChildViewAdded(parent: View?, child: View?) {
                    child!!.post({
                        enhanceAttachedPage(child)
                        updateVersionLabels()
                        refreshTestUi()
                    })
                }

                public override fun onChildViewRemoved(parent: View?, child: View?) {}
            }
        )
        if (host!!.getChildCount() > 0) enhanceAttachedPage(host!!.getChildAt(0))
    }

    private fun installHistoryButton() {
        if (historyButtonAdded) return
        val content: View? = findViewById<View?>(android.R.id.content)
        if (!(content is ViewGroup)) return
        val cg: ViewGroup? = content as ViewGroup?
        if (cg!!.getChildCount() == 0 || !(cg!!.getChildAt(0) is ViewGroup)) return
        val root: ViewGroup? = cg!!.getChildAt(0) as ViewGroup?
        if (root!!.getChildCount() == 0 || !(root!!.getChildAt(0) is LinearLayout)) return
        val bar: LinearLayout? = root!!.getChildAt(0) as LinearLayout?
        val button: TextView? = makeText("▤", 20, true, Color.WHITE)
        button!!.setGravity(Gravity.CENTER)
        button!!.setPadding(0, 0, 0, 0)
        button!!.setContentDescription("Сеансы и журнал")
        button!!.setBackgroundColor(Color.TRANSPARENT)
        button!!.setOnClickListener({ v ->
            startActivity(Intent(this, HistoryActivity::class.java))
        })
        val index: Int = Math.max(0, bar!!.getChildCount() - 1)
        bar!!.addView(button, index, LinearLayout.LayoutParams(dp(36), dp(42)))
        normalizeNavigationIcons(bar)
        historyButtonAdded = true
    }

    private fun normalizeNavigationIcons(bar: LinearLayout?) {
        if (bar == null) return
        val first: Int = Math.max(0, bar!!.getChildCount() - 3)
        for (i: Int in first until bar!!.getChildCount()) {
            val child: View? = bar!!.getChildAt(i)
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(36), dp(42))
            lp.setMargins(0, 0, 0, 0)
            child!!.setLayoutParams(lp)
            child!!.setPadding(0, 0, 0, 0)
            if (child is TextView) (child as TextView).setGravity(Gravity.CENTER)
        }
    }

    private fun enhanceAttachedPage(attached: View?) {
        val page: View? = unwrapScroll(attached)
        if (page == null) return
        if (containsText(page, "График температуры") && findCheck(page, "Камера") != null)
            enhanceGraphPage(page)
        else if (containsText(page, "MQTT подключение")) enhanceSettingsPage(page)
        else if (containsText(page, "Связь") && containsText(page, "Удалённое управление"))
            enhanceMonitorPage(page)
    }

    private fun unwrapScroll(v: View?): View? {
        if (v is ScrollView) {
            val s: ScrollView? = v as ScrollView?
            return if (s!!.getChildCount() > 0) s!!.getChildAt(0) else null
        }
        return v
    }

    private fun enhanceMonitorPage(pageView: View?) {
        if (testBanner != null || !(pageView is LinearLayout)) return
        val page: LinearLayout? = pageView as LinearLayout?
        val card: LinearLayout = LinearLayout(this)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setPadding(dp(12), dp(8), dp(12), dp(8))
        card.setBackground(roundStroke(WARN_BG, 16, ORANGE, 1))
        testBannerTitle = makeText("ТЕСТОВЫЕ ДАННЫЕ", 14, true, ORANGE)
        card.addView(testBannerTitle)
        testBannerDetail =
            makeText("Локальная симуляция · MQTT-команды заблокированы", 11, false, MUTED)
        testBannerDetail!!.setPadding(0, dp(2), 0, 0)
        card.addView(testBannerDetail)
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(8), dp(8), dp(8), dp(4))
        page!!.addView(card, 0, lp)
        testBanner = card
        testBanner!!.setVisibility(if (testRunning) View.VISIBLE else View.GONE)
    }

    private fun enhanceSettingsPage(page: View?) {
        if (!(page is ViewGroup)) return
        val interfaceTitle: TextView? = findText(page, "Интерфейс")
        if (
            interfaceTitle == null ||
                !(interfaceTitle!!.getParent() is View) ||
                !((interfaceTitle!!.getParent() as View).getParent() is ViewGroup)
        )
            return
        val interfaceCard: View? = interfaceTitle!!.getParent() as View?
        val parent: ViewGroup? = interfaceCard!!.getParent() as ViewGroup?

        if (!notificationCardAdded) {
            val card: LinearLayout = LinearLayout(this)
            card.setOrientation(LinearLayout.VERTICAL)
            card.setPadding(dp(12), dp(10), dp(12), dp(10))
            card.setBackground(roundStroke(CARD, 18, BORDER, 1))
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1).toFloat())
            card.addView(makeText("Уведомления", 18, true, TEXT))
            val connection: CheckBox? =
                settingCheck("Потеря и восстановление связи", "notify_connection", true)
            val target: CheckBox? = settingCheck("Камера достигла уставки", "notify_setpoint", true)
            val sessions: CheckBox? =
                settingCheck("Начало и завершение сеанса", "notify_session", false)
            card.addView(connection, checkParams())
            card.addView(target, checkParams())
            card.addView(sessions, checkParams())
            val hint: TextView? =
                makeText(
                    "Уведомления формируются локально по уже существующей телеметрии. Для Android 13+ требуется системное разрешение.",
                    11,
                    false,
                    MUTED,
                )
            hint!!.setPadding(0, dp(3), 0, 0)
            card.addView(hint)
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(dp(8), dp(4), dp(8), dp(4))
            parent!!.addView(card, parent!!.indexOfChild(interfaceCard) + 1, lp)
            notificationCardAdded = true
        }
        if (!testCardAdded) {
            var notificationCard: View? = null
            val nt: TextView? = findText(page, "Уведомления")
            if (nt != null && nt!!.getParent() is View) notificationCard = nt!!.getParent() as View?
            val insert: Int =
                if (notificationCard != null) parent!!.indexOfChild(notificationCard) + 1
                else parent!!.indexOfChild(interfaceCard) + 1
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(dp(8), dp(4), dp(8), dp(4))
            parent!!.addView(buildTestCard(), Math.max(0, insert), lp)
            testCardAdded = true
        }
        refreshTestUi()
    }

    private fun buildTestCard(): View? {
        val card: LinearLayout = LinearLayout(this)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setPadding(dp(12), dp(10), dp(12), dp(10))
        card.setBackground(roundStroke(CARD, 18, BORDER, 1))
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1).toFloat())
        card.addView(makeText("Тестовый режим", 18, true, TEXT))
        val warning: TextView? =
            makeText(
                "Только локальная симуляция. При запуске MQTT отключается, команды HomeSmoke/Arduino не публикуются.",
                11,
                true,
                ORANGE,
            )
        warning!!.setPadding(0, dp(4), 0, dp(6))
        warning!!.setLineSpacing(0f, 1.05f)
        card.addView(warning)
        val scenarioLabel: TextView? = makeText("Сценарий", 12, true, MUTED)
        scenarioLabel!!.setPadding(0, dp(2), 0, dp(3))
        card.addView(scenarioLabel)
        testScenarioSpinner = Spinner(this)
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, TEST_SCENARIOS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        testScenarioSpinner!!.setAdapter(adapter)
        testScenarioIndex =
            Math.max(
                0,
                Math.min(TEST_SCENARIOS!!.size - 1, remotePrefs!!.getInt("test_scenario", 0)),
            )
        testScenarioSpinner!!.setSelection(testScenarioIndex)
        testScenarioSpinner!!.setOnItemSelectedListener(
            object : AdapterView.OnItemSelectedListener {
                public override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    testScenarioIndex = position
                    remotePrefs!!.edit()!!.putInt("test_scenario", position)!!.apply()
                }

                public override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        )
        card.addView(testScenarioSpinner, LinearLayout.LayoutParams(-1, dp(46)))
        val buttons: LinearLayout = LinearLayout(this)
        buttons.setGravity(Gravity.CENTER_VERTICAL)
        testStartButton = testButton("Запустить тест", BLUE)
        testStopButton = testButton("Остановить", OFF)
        testStartButton!!.setOnClickListener({ v -> startTestScenario() })
        testStopButton!!.setOnClickListener({ v -> stopTestScenario(true) })
        val left: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        left.setMargins(0, dp(7), dp(4), 0)
        val right: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        right.setMargins(dp(4), dp(7), 0, 0)
        buttons.addView(testStartButton, left)
        buttons.addView(testStopButton, right)
        card.addView(buttons)
        testStatus = makeText("Тестовый режим выключен", 11, false, MUTED)
        testStatus!!.setPadding(0, dp(6), 0, 0)
        card.addView(testStatus)
        val hint: TextView? =
            makeText(
                "Симуляция проверяет главный экран, график, сеансы, журнал и локальные уведомления. Тестовые сеансы помечаются в истории.",
                11,
                false,
                MUTED,
            )
        hint!!.setPadding(0, dp(5), 0, 0)
        hint!!.setLineSpacing(0f, 1.05f)
        card.addView(hint)
        return card
    }

    private fun testButton(text: String?, color: Int): Button? {
        val b: Button = Button(this)
        b.setText(text)
        b.setTextSize(12f)
        b.setTextColor(Color.WHITE)
        b.setTypeface(Typeface.DEFAULT_BOLD)
        b.setAllCaps(false)
        b.setGravity(Gravity.CENTER)
        b.setPadding(dp(5), 0, dp(5), 0)
        b.setBackground(roundStroke(color, 13, color, 1))
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null)
        return b
    }

    private fun settingCheck(label: String?, key: String?, def: Boolean): CheckBox? {
        val c: CheckBox = CheckBox(this)
        c.setText(label)
        c.setTextSize(13f)
        c.setTextColor(TEXT)
        c.setTypeface(Typeface.DEFAULT_BOLD)
        c.setGravity(Gravity.CENTER_VERTICAL)
        c.setChecked(remotePrefs!!.getBoolean(key, def))
        c.setOnCheckedChangeListener({ b, checked ->
            remotePrefs!!.edit()!!.putBoolean(key, checked)!!.apply()
            if (checked) ensureNotificationPermission(true)
        })
        return c
    }

    private fun checkParams(): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(42))
        p.setMargins(0, dp(2), 0, 0)
        return p
    }

    private fun enhanceGraphPage(pageView: View?) {
        if (graphEnhanced || !(pageView is ViewGroup)) return
        graphPage = pageView as ViewGroup?
        val duplicateTitle: TextView? = findText(graphPage, "График температуры")
        if (duplicateTitle != null && duplicateTitle!!.getParent() is View) {
            val intro: View? = duplicateTitle!!.getParent() as View?
            if (intro!!.getParent() is ViewGroup) {
                val parent: ViewGroup? = intro!!.getParent() as ViewGroup?
                val index: Int = parent!!.indexOfChild(intro)
                parent!!.removeView(intro)
                parent!!.addView(buildRecordCard(), Math.max(0, index))
            }
        }
        graphChartView = findChart(graphPage)
        if (graphChartView != null && graphChartView!!.getParent() is ViewGroup) {
            val chartCard: ViewGroup? = graphChartView!!.getParent() as ViewGroup?
            val chartIndex: Int = chartCard!!.indexOfChild(graphChartView)
            graphLiveValues = makeText("", 11, true, TEXT)
            graphLiveValues!!.setLineSpacing(0f, 1.04f)
            graphLiveValues!!.setPadding(0, 0, 0, dp(5))
            graphLiveValues!!.setVisibility(View.GONE)
            chartCard!!.addView(graphLiveValues, Math.max(0, chartIndex))
            graphEmptyState =
                makeText(
                    "История пока пуста\nГрафик появится автоматически после получения свежих данных.",
                    13,
                    false,
                    MUTED,
                )
            graphEmptyState!!.setGravity(Gravity.CENTER)
            graphEmptyState!!.setLineSpacing(0f, 1.10f)
            graphEmptyState!!.setPadding(dp(12), dp(14), dp(12), dp(14))
            chartCard!!.addView(graphEmptyState, Math.max(0, chartIndex + 1))
            graphHeaterScale = makeText("", 11, true, MUTED)
            graphHeaterScale!!.setGravity(Gravity.CENTER)
            graphHeaterScale!!.setPadding(0, dp(4), 0, 0)
            setHeaterScaleText()
            chartCard!!.addView(graphHeaterScale)
            graphBaseSummary = findTextStarting(chartCard, "Свежих данных")
            if (graphBaseSummary == null)
                graphBaseSummary = findTextStarting(chartCard, "В текущем сеансе")
        }
        decorateSeriesCheck(findCheck(graphPage, "Камера"), CAMERA, "Камера")
        decorateSeriesCheck(findCheck(graphPage, "Уставка"), SETPOINT, "Уставка")
        decorateSeriesCheck(findCheck(graphPage, "Щуп K"), PROBE_K, "Щуп K")
        decorateSeriesCheck(findCheck(graphPage, "Щуп T"), PROBE_T, "Щуп T")
        val heaterHint: TextView? = findTextStarting(graphPage, "Мощность ТЭНа отображается")
        if (heaterHint != null)
            heaterHint!!.setText("Цвет = линия графика · ТЭН — отдельная шкала 0–100 %")
        val pointTitle: TextView? = findText(graphPage, "Точка графика")
        if (pointTitle != null && pointTitle!!.getParent() is View)
            graphPointCard = pointTitle!!.getParent() as View?
        graphEnhanced = true
        refreshGraphUx()
    }

    private fun buildRecordCard(): View? {
        val card: LinearLayout = LinearLayout(this)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setPadding(dp(12), dp(8), dp(12), dp(8))
        card.setBackground(roundStroke(CARD, 18, BORDER, 1))
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1).toFloat())
        graphRecordStatus = makeText("Ожидание телеметрии", 14, true, ORANGE)
        graphRecordStatus!!.setPadding(0, 0, 0, dp(1))
        card.addView(graphRecordStatus)
        card.addView(
            makeText(
                "Локальная история · до 24 ч · старые данные брокера не сохраняются",
                11,
                false,
                MUTED,
            )
        )
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(dp(8), dp(8), dp(8), dp(4))
        card.setLayoutParams(lp)
        return card
    }

    private fun startTestScenario() {
        if (testRunning) return
        val now: Long = System.currentTimeMillis()
        val latest: Long = if (uxHistory == null) 0L else uxHistory!!.latestTimestamp()
        if (latest > 0 && now - latest < SESSION_SPLIT_MS && !isTestTimestamp(latest)) {
            toast(
                "Есть недавняя реальная телеметрия. Тестовый режим сейчас не запускается, чтобы не смешивать данные."
            )
            return
        }
        testScenarioIndex =
            if (testScenarioSpinner == null) testScenarioIndex
            else testScenarioSpinner!!.getSelectedItemPosition()
        previousWantConnection = readMainBoolean("wantConnection", false)
        writeMainBoolean("wantConnection", false)
        invokeMain("disconnectInternal", arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType), true)
        testRunning = true
        testStartedAt = now
        liveStateKnown = false
        setpointStateKnown = false
        withinSetpoint = false
        lastObservedTarget = java.lang.Double.NaN
        remotePrefs!!
            .edit()!!
            .putBoolean("test_mode_active", true)!!
            .putLong("test_active_start", now)!!
            .putString("test_active_name", TEST_SCENARIOS!![testScenarioIndex])!!
            .putInt("test_scenario", testScenarioIndex)!!
            .apply()
        if (operational != null)
            operational!!.addEvent(
                now,
                "test",
                "ТЕСТ · сценарий «" + TEST_SCENARIOS!![testScenarioIndex] + "» запущен",
            )
        refreshTestUi()
        graphUxHandler.removeCallbacks(simulationTick)
        graphUxHandler.post(simulationTick)
    }

    private fun stopTestScenario(reconnect: Boolean) {
        if (!testRunning) return
        graphUxHandler.removeCallbacks(simulationTick)
        val now: Long = System.currentTimeMillis()
        refreshOperationalFeatures()
        if (operational != null) {
            val sessions: List<OperationalHistoryStore.Session?>? = operational!!.querySessions(1)
            if (!sessions!!.isEmpty()) {
                val s: OperationalHistoryStore.Session? = sessions!!.get(0)
                if (s!!.active() && s!!.startTs >= testStartedAt - 1000L) {
                    val tr: OperationalHistoryStore.Transition? =
                        operational!!.closeIfInactive(now + SESSION_SPLIT_MS + 1000L)
                    if (tr != null) handleSessionTransition(tr)
                }
            }
        }
        val name: String? = TEST_SCENARIOS!![testScenarioIndex]
        testRunning = false
        recordTestInterval(testStartedAt, now, name)
        remotePrefs!!
            .edit()!!
            .putBoolean("test_mode_active", false)!!
            .remove("test_active_start")!!
            .remove("test_active_name")!!
            .apply()
        if (operational != null)
            operational!!.addEvent(now, "test", "ТЕСТ · сценарий «" + name + "» завершён")
        writeMainLong("lastTelemetryAt", 0L)
        invokeMain(
            "applyTelemetryFreshness",
            arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType),
            false,
        )
        invokeMain("updateLastDataCaption", arrayOfNulls<Class<*>?>(0))
        refreshTestUi()
        refreshGraphUx()
        if (reconnect && previousWantConnection) {
            writeMainBoolean("wantConnection", true)
            graphUxHandler.postDelayed(
                {
                    invokeMain(
                        "connectMqtt",
                        arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType),
                        false,
                    )
                },
                600L,
            )
        }
    }

    private fun shouldEmitTestFrame(scenario: Int, elapsed: Double): Boolean {
        if (scenario != 5) return true
        val phase: Double = elapsed % 55.0
        return phase < 20.0 || phase >= 38.0
    }

    private fun buildTestFrame(scenario: Int, sec: Double): TestFrame? {
        if (scenario == 1) {
            val p: Double = Math.min(1.0, (sec % 110.0) / 90.0)
            val cam: Double = 25.0 + 45.0 * p
            return TestFrame(
                cam,
                70.0,
                25.0 + 31.0 * p,
                24.0 + 25.0 * p,
                Math.max(18.0, 100.0 - 72.0 * p),
                "1",
                false,
                "—",
                0,
                "PID нагрев",
            )
        }
        if (scenario == 2) {
            val decay: Double = Math.exp(-(sec % 120.0) / 24.0)
            val cam: Double = 65.0 - 4.2 * decay * Math.cos(sec / 5.0)
            val heater: Double = 28.0 + 18.0 * Math.max(0.0, Math.sin(sec / 7.0))
            return TestFrame(
                cam,
                65.0,
                54.0 + 0.4 * Math.sin(sec / 9.0),
                52.5 + 0.3 * Math.sin(sec / 11.0),
                heater,
                "1",
                false,
                "—",
                0,
                "PID стабилизация",
            )
        }
        if (scenario == 3) {
            val phase: Double = sec % 120.0
            val stage: Int = if (phase < 40) 1 else (if (phase < 80) 2 else 3)
            val set: Double = if (stage == 1) 45.0 else (if (stage == 2) 60.0 else 70.0)
            val cam: Double = set - 2.0 + 1.2 * Math.sin(sec / 8.0)
            val pk: Double = if (stage == 1) 34.0 else (if (stage == 2) 48.0 else 61.0)
            return TestFrame(
                cam,
                set,
                pk,
                pk - 2.0,
                32.0 + 18.0 * Math.max(0.0, Math.sin(sec / 6.0)),
                "2",
                true,
                "Тестовая Auto",
                stage,
                "Тест · этап " + stage,
            )
        }
        if (scenario == 4) {
            val p: Double = Math.min(1.0, (sec % 110.0) / 85.0)
            val pk: Double = 35.0 + 33.0 * p
            return TestFrame(
                69.3 + 0.5 * Math.sin(sec / 8.0),
                70.0,
                pk,
                pk - 3.0,
                24.0 + 8.0 * Math.sin(sec / 10.0),
                "1",
                false,
                "—",
                0,
                if (pk >= 67.5) "Щуп у цели" else "Нагрев продукта",
            )
        }
        if (scenario == 5) {
            return TestFrame(
                60.0 + 0.3 * Math.sin(sec / 6.0),
                60.0,
                50.0 + 0.2 * Math.sin(sec / 8.0),
                48.0 + 0.2 * Math.sin(sec / 9.0),
                28.0,
                "1",
                false,
                "—",
                0,
                "Тест связи",
            )
        }
        val phase: Double = sec % 180.0
        if (phase < 60.0) {
            val p: Double = phase / 60.0
            return TestFrame(
                25.0 + 35.0 * p,
                60.0,
                25.0 + 21.0 * p,
                24.0 + 18.0 * p,
                96.0 - 45.0 * p,
                "1",
                false,
                "—",
                0,
                "Разогрев",
            )
        }
        if (phase < 120.0) {
            val x: Double = phase - 60.0
            return TestFrame(
                60.0 + 0.7 * Math.sin(x / 7.0),
                60.0,
                46.0 + 0.18 * x,
                43.0 + 0.15 * x,
                30.0 + 12.0 * Math.max(0.0, Math.sin(x / 6.0)),
                "1",
                false,
                "—",
                0,
                "Стабилизация",
            )
        }
        val x: Double = phase - 120.0
        val stage: Int = if (x < 30) 1 else 2
        val set: Double = if (stage == 1) 65.0 else 70.0
        return TestFrame(
            set - 2.0 + 0.8 * Math.sin(x / 7.0),
            set,
            57.0 + 0.16 * x,
            54.0 + 0.14 * x,
            36.0,
            "2",
            true,
            "Демо-копчение",
            stage,
            "Тест · Auto этап " + stage,
        )
    }

    private fun injectTestFrame(f: TestFrame?, ts: Long) {
        try {
            val o: JSONObject = JSONObject()
            o.put("temp_ds", round1(f!!.camera))
            o.put("temp_tip_k", round1(f!!.probeK))
            o.put("temp_tip_t", round1(f!!.probeT))
            o.put("temp_k", round1(f!!.setpoint))
            o.put("heater_power", round1(f!!.heater))
            o.put("mode", f!!.mode)
            o.put("last_command", "TEST_LOCAL")
            o.put("android_auto_program", f!!.program)
            o.put("android_auto_status", f!!.autoStatus)
            o.put("android_auto_stage", f!!.stage)
            o.put("android_auto_running", f!!.autoRunning)
            o.put("device_id", "TEST")
            o.put("ts", ts)
            invokeMain("status", arrayOf<Class<*>?>(String::class.java), o.toString())
        } catch (e: Exception) {
            if (operational != null)
                operational!!.addEvent(
                    System.currentTimeMillis(),
                    "test",
                    "ТЕСТ · ошибка симуляции: " + e!!.javaClass.getSimpleName()!!,
                )
        }
    }

    private fun testPhaseText(): String? {
        if (!testRunning) return "Тестовый режим выключен"
        val sec: Long = Math.max(0, (System.currentTimeMillis() - testStartedAt) / 1000L)
        if (testScenarioIndex == 5) {
            val phase: Long = sec % 55L
            if (phase >= 20 && phase < 38)
                return "ТЕСТ · потеря телеметрии · " + (38 - phase) + " сек до восстановления"
        }
        return "ТЕСТ · " + TEST_SCENARIOS!![testScenarioIndex] + " · " + sec.toString() + " сек"
    }

    private fun refreshTestUi() {
        if (testStatus != null) {
            testStatus!!.setText(testPhaseText())
            testStatus!!.setTextColor(if (testRunning) ORANGE else MUTED)
        }
        if (testStartButton != null) {
            testStartButton!!.setEnabled(!testRunning)
            testStartButton!!.setAlpha(if (testRunning) .45f else 1f)
        }
        if (testStopButton != null) {
            testStopButton!!.setEnabled(testRunning)
            testStopButton!!.setAlpha(if (testRunning) 1f else .45f)
            testStopButton!!.setBackground(
                roundStroke(if (testRunning) RED else OFF, 13, if (testRunning) RED else OFF, 1)
            )
        }
        if (testScenarioSpinner != null) testScenarioSpinner!!.setEnabled(!testRunning)
        if (testBanner != null)
            testBanner!!.setVisibility(if (testRunning) View.VISIBLE else View.GONE)
        if (testBannerTitle != null && testRunning)
            testBannerTitle!!.setText("ТЕСТОВЫЕ ДАННЫЕ · " + TEST_SCENARIOS!![testScenarioIndex])
        if (testBannerDetail != null && testRunning)
            testBannerDetail!!.setText(
                "Локальная симуляция · MQTT отключён · команды не отправляются"
            )
        if (testRunning) {
            val availability: TextView? =
                findTextStarting(findViewById<View?>(android.R.id.content), "Управление недоступно")
            if (availability != null)
                availability!!.setText("Управление недоступно · тестовый режим")
        }
    }

    private fun refreshOperationalFeatures() {
        if (uxHistory == null || operational == null) return
        val now: Long = System.currentTimeMillis()
        val processed: Long = operational!!.lastProcessedTs
        val pending: List<TelemetryHistoryStore.Sample?>? =
            uxHistory!!.query(Math.max(now - HISTORY_MS, processed + 1L), now, 0)
        val transitions: List<OperationalHistoryStore.Transition?>? =
            operational!!.processSamples(pending)
        val closed: OperationalHistoryStore.Transition? = operational!!.closeIfInactive(now)
        for (t: OperationalHistoryStore.Transition? in transitions!!) handleSessionTransition(t)
        if (closed != null) handleSessionTransition(closed)
        val lastList: List<TelemetryHistoryStore.Sample?>? =
            uxHistory!!.query(now - HISTORY_MS, now, 2)
        val last: TelemetryHistoryStore.Sample? =
            if (lastList!!.isEmpty()) null else lastList!!.get(lastList!!.size - 1)
        handleConnectionAndSetpoint(last, now)
        recordCommandEvents()
    }

    private fun handleSessionTransition(transition: OperationalHistoryStore.Transition?) {
        if (transition == null || transition!!.session == null) return
        if (!remotePrefs!!.getBoolean("notify_session", false)) return
        val test: Boolean =
            testRunning ||
                isTestSession(transition!!.session!!.startTs, transition!!.session!!.effectiveEnd())
        val prefix: String = if (test) "ТЕСТ · " else ""
        if (transition!!.kind == OperationalHistoryStore.Transition.START)
            postNotification(2203, "HomeSmoke Remote", prefix + "Начат новый сеанс копчения")
        else
            postNotification(
                2203,
                "HomeSmoke Remote",
                prefix + "Сеанс завершён · " + duration(transition!!.session!!.durationMs()),
            )
    }

    private fun handleConnectionAndSetpoint(last: TelemetryHistoryStore.Sample?, now: Long) {
        val testSource: Boolean = last != null && isTestTimestamp(last!!.ts)
        if (testSource && !testRunning) {
            liveStateKnown = false
            setpointStateKnown = false
            withinSetpoint = false
            return
        }
        val live: Boolean = last != null && Math.abs(now - last!!.ts) <= LIVE_MS
        if (!liveStateKnown) {
            liveStateKnown = true
            lastLiveState = live
        } else if (live != lastLiveState) {
            var message: String? =
                if (live) "Связь с коптильней восстановлена"
                else "Коптильня перестала передавать свежие данные"
            if (testRunning) message = "ТЕСТ · " + message!!
            operational!!.addEvent(now, if (testRunning) "test" else "connection", message)
            if (remotePrefs!!.getBoolean("notify_connection", true))
                postNotification(2201, "HomeSmoke Remote", message)
            lastLiveState = live
        }
        if (
            !live ||
                last == null ||
                java.lang.Double.isNaN(last!!.camera) ||
                java.lang.Double.isNaN(last!!.setpoint) ||
                last!!.setpoint <= 0
        ) {
            setpointStateKnown = false
            withinSetpoint = false
            return
        }
        val diff: Double = Math.abs(last!!.camera - last!!.setpoint)
        if (!setpointStateKnown) {
            setpointStateKnown = true
            lastObservedTarget = last!!.setpoint
            withinSetpoint = diff <= 1.0
            return
        }
        if (
            java.lang.Double.isNaN(lastObservedTarget) ||
                Math.abs(lastObservedTarget - last!!.setpoint) > 0.1
        ) {
            lastObservedTarget = last!!.setpoint
            withinSetpoint = false
        }
        if (diff <= 1.0 && !withinSetpoint) {
            withinSetpoint = true
            val message: String? =
                (if (testRunning) "ТЕСТ · " else "") +
                    "Камера достигла уставки " +
                    String.format(Locale.getDefault(), "%.1f", last!!.setpoint) +
                    " °C"
            operational!!.addEvent(now, if (testRunning) "test" else "info", message)
            if (remotePrefs!!.getBoolean("notify_setpoint", true))
                postNotification(2202, "HomeSmoke Remote", message)
        } else if (diff > 2.0) withinSetpoint = false
    }

    private fun recordCommandEvents() {
        if (remotePrefs == null || operational == null || testRunning) return
        val raw: String? = remotePrefs!!.getString("command_history", "[]")
        var first: String? = ""
        try {
            val a: JSONArray = JSONArray(raw)
            if (a.length() > 0) first = a.optString(0, "")
        } catch (ignored: Exception) {}

        val initialized: Boolean = remotePrefs!!.getBoolean("ops_command_initialized", false)
        if (!initialized) {
            remotePrefs!!
                .edit()!!
                .putBoolean("ops_command_initialized", true)!!
                .putString("ops_last_command_seen", first)!!
                .apply()
            return
        }
        val seen: String? = remotePrefs!!.getString("ops_last_command_seen", "")
        if (!first!!.isEmpty() && first != seen) {
            operational!!.addEvent(
                System.currentTimeMillis(),
                "command",
                "Команда Remote · " + first!!,
            )
            remotePrefs!!.edit()!!.putString("ops_last_command_seen", first)!!.apply()
        }
    }

    private fun refreshGraphUx() {
        if (!graphEnhanced || uxHistory == null) return
        val now: Long = System.currentTimeMillis()
        val samples: List<TelemetryHistoryStore.Sample?>? =
            uxHistory!!.query(now - HISTORY_MS, now, 2)
        val last: TelemetryHistoryStore.Sample? =
            if (samples!!.isEmpty()) null else samples!!.get(samples!!.size - 1)
        val live: Boolean = last != null && Math.abs(now - last!!.ts) <= LIVE_MS
        val test: Boolean = last != null && isTestTimestamp(last!!.ts)
        if (graphRecordStatus != null) {
            if (testRunning) {
                graphRecordStatus!!.setText("● ТЕСТОВАЯ ЗАПИСЬ")
                graphRecordStatus!!.setTextColor(ORANGE)
            } else if (last == null) {
                graphRecordStatus!!.setText("Ожидание телеметрии")
                graphRecordStatus!!.setTextColor(ORANGE)
            } else if (test) {
                graphRecordStatus!!.setText("Тестовая запись завершена")
                graphRecordStatus!!.setTextColor(MUTED)
            } else if (live) {
                graphRecordStatus!!.setText("● Запись данных")
                graphRecordStatus!!.setTextColor(GREEN)
            } else {
                graphRecordStatus!!.setText("Нет свежих данных")
                graphRecordStatus!!.setTextColor(MUTED)
            }
        }
        if (graphLiveValues != null) {
            if (last == null) graphLiveValues!!.setVisibility(View.GONE)
            else {
                val prefix: String =
                    if (test) (if (live) "Тест: " else "Последний тест: ")
                    else (if (live) "Сейчас: " else "Последние: ")
                graphLiveValues!!.setText(
                    prefix +
                        "Камера " +
                        value(last!!.camera, "°") +
                        " · Уставка " +
                        value(last!!.setpoint, "°") +
                        " · K " +
                        value(last!!.probeK, "°") +
                        " · T " +
                        value(last!!.probeT, "°") +
                        " · ТЭН " +
                        value(last!!.heater, "%")
                )
                graphLiveValues!!.setTextColor(if (test) ORANGE else (if (live) TEXT else MUTED))
                graphLiveValues!!.setVisibility(View.VISIBLE)
            }
        }
        val any: Boolean = last != null
        if (graphChartView != null) {
            graphChartView!!.setVisibility(if (any) View.VISIBLE else View.GONE)
            val lp: ViewGroup.LayoutParams? = graphChartView!!.getLayoutParams()
            if (lp != null) {
                lp!!.height = dp(300)
                graphChartView!!.setLayoutParams(lp)
            }
        }
        if (graphEmptyState != null)
            graphEmptyState!!.setVisibility(if (any) View.GONE else View.VISIBLE)
        if (graphHeaterScale != null)
            graphHeaterScale!!.setVisibility(if (any) View.VISIBLE else View.GONE)
        if (graphBaseSummary != null)
            graphBaseSummary!!.setVisibility(if (any) View.VISIBLE else View.GONE)
        if (graphPointCard != null)
            graphPointCard!!.setVisibility(if (any) View.VISIBLE else View.GONE)
    }

    private fun recordTestInterval(start: Long, end: Long, name: String?) {
        if (start <= 0 || end < start) return
        try {
            val old: JSONArray = JSONArray(remotePrefs!!.getString("test_intervals", "[]"))
            val out: JSONArray = JSONArray()
            val from: Int = Math.max(0, old.length() - 19)
            for (i: Int in from until old.length()) out.put(old.get(i))
            val x: JSONObject = JSONObject()
            x.put("start", start)
            x.put("end", end)
            x.put("name", if (name == null) "Тест" else name)
            out.put(x)
            remotePrefs!!.edit()!!.putString("test_intervals", out.toString())!!.apply()
        } catch (ignored: Exception) {}
    }

    private fun recoverInterruptedTestInterval() {
        if (!remotePrefs!!.getBoolean("test_mode_active", false)) return
        val start: Long = remotePrefs!!.getLong("test_active_start", 0L)
        val name: String? = remotePrefs!!.getString("test_active_name", "Тест")
        if (start > 0) recordTestInterval(start, System.currentTimeMillis(), name!! + " · прерван")
        remotePrefs!!
            .edit()!!
            .putBoolean("test_mode_active", false)!!
            .remove("test_active_start")!!
            .remove("test_active_name")!!
            .apply()
    }

    private fun isTestTimestamp(ts: Long): Boolean {
        if (ts <= 0) return false
        if (testRunning && ts >= testStartedAt) return true
        try {
            val a: JSONArray = JSONArray(remotePrefs!!.getString("test_intervals", "[]"))
            for (i: Int in 0 until a.length()) {
                val x: JSONObject? = a.optJSONObject(i)
                if (x == null) continue
                val s: Long = x!!.optLong("start", 0)
                val e: Long = x!!.optLong("end", 0)
                if (s > 0 && e >= s && ts >= s && ts <= e) return true
            }
        } catch (ignored: Exception) {}

        return false
    }

    private fun isTestSession(start: Long, end: Long): Boolean {
        if (testRunning && end >= testStartedAt && start <= System.currentTimeMillis()) return true
        try {
            val a: JSONArray = JSONArray(remotePrefs!!.getString("test_intervals", "[]"))
            for (i: Int in 0 until a.length()) {
                val x: JSONObject? = a.optJSONObject(i)
                if (x == null) continue
                val s: Long = x!!.optLong("start", 0)
                val e: Long = x!!.optLong("end", 0)
                if (s > 0 && e >= s && start <= e && end >= s) return true
            }
        } catch (ignored: Exception) {}

        return false
    }

    private fun invokeMain(name: String?, types: Array<Class<*>?>?, vararg args: Any?) {
        try {
            val m: Method? = MainActivity::class.java!!.getDeclaredMethod(name, *types!!)
            m!!.setAccessible(true)
            m!!.invoke(this, *args)
        } catch (e: Exception) {
            if (operational != null && testRunning)
                operational!!.addEvent(
                    System.currentTimeMillis(),
                    "test",
                    "ТЕСТ · внутренняя ошибка " + name!!,
                )
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm: NotificationManager? =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null) return
        val ch: NotificationChannel =
            NotificationChannel(
                CHANNEL_ID,
                "HomeSmoke Remote",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        ch.setDescription("Состояние коптильни и температурные события")
        nm!!.createNotificationChannel(ch)
    }

    private fun ensureNotificationPermission(userAction: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        if (
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
            return
        val asked: Boolean = remotePrefs!!.getBoolean("notify_permission_asked", false)
        if (userAction || !asked) {
            remotePrefs!!.edit()!!.putBoolean("notify_permission_asked", true)!!.apply()
            requestPermissions(arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
    }

    private fun postNotification(id: Int, title: String?, body: String?) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val nm: NotificationManager? =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null) return
        val open: Intent = Intent(this, GraphUxActivity::class.java)
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags: Int =
            if (Build.VERSION.SDK_INT >= 23)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        val pi: PendingIntent? = PendingIntent.getActivity(this, id, open, flags)
        val b: Notification.Builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_notify_more)!!
            .setContentTitle(title)!!
            .setContentText(body)!!
            .setAutoCancel(true)!!
            .setContentIntent(pi)
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT)
        nm!!.notify(id, b.build())
    }

    private fun setHeaterScaleText() {
        if (graphHeaterScale == null) return
        val text: String = "━  ТЭН, %   ·   0 — 50 — 100"
        val span: SpannableString = SpannableString(text)
        span.setSpan(ForegroundColorSpan(HEATER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        graphHeaterScale!!.setText(span)
    }

    private fun decorateSeriesCheck(box: CheckBox?, color: Int, label: String?) {
        if (box == null) return
        val span: SpannableString = SpannableString("━  " + label!!)
        span.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        box!!.setText(span)
    }

    private fun updateVersionLabels() {
        val content: View? = findViewById<View?>(android.R.id.content)
        if (content != null) replaceVersionText(content)
    }

    private fun replaceVersionText(root: View?) {
        if (root is TextView) {
            val tv: TextView? = root as TextView?
            val cs: CharSequence? = tv!!.getText()
            if (cs != null) {
                val s: String? = cs!!.toString()
                if (
                    s!!.startsWith("HomeSmoke Remote ") &&
                        s!!.matches(("HomeSmoke Remote \\d+\\.\\d+\\.\\d+.*").toRegex())
                ) {
                    val replacement: String? =
                        s!!.replaceFirst(
                            ("HomeSmoke Remote \\d+\\.\\d+\\.\\d+").toRegex(),
                            "HomeSmoke Remote " + appVersion()!!,
                        )
                    if (replacement != s) tv!!.setText(replacement)
                }
            }
        }
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) replaceVersionText(g!!.getChildAt(i))
        }
    }

    private fun appVersion(): String? {
        try {
            val version: String? =
                getPackageManager()!!.getPackageInfo(getPackageName(), 0)!!.versionName
            return if (version == null || version!!.trim({ it <= ' ' }).isEmpty()) "2.0.18"
            else version
        } catch (ignored: Exception) {
            return "2.0.18"
        }
    }

    private fun containsText(root: View?, exact: String?): Boolean {
        return findText(root, exact) != null
    }

    private fun findText(root: View?, exact: String?): TextView? {
        if (root is TextView && exact!!.contentEquals((root as TextView).getText()))
            return root as TextView?
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val found: TextView? = findText(g!!.getChildAt(i), exact)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findTextStarting(root: View?, prefix: String?): TextView? {
        if (root is TextView) {
            val cs: CharSequence? = (root as TextView).getText()
            if (cs != null && cs!!.toString()!!.startsWith(prefix!!)) return root as TextView?
        }
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val found: TextView? = findTextStarting(g!!.getChildAt(i), prefix)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findCheck(root: View?, exact: String?): CheckBox? {
        if (root is CheckBox && exact!!.contentEquals((root as CheckBox).getText()))
            return root as CheckBox?
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val found: CheckBox? = findCheck(g!!.getChildAt(i), exact)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findChart(root: View?): TemperatureChartView? {
        if (root is TemperatureChartView) return root as TemperatureChartView?
        if (root is ViewGroup) {
            val g: ViewGroup? = root as ViewGroup?
            for (i: Int in 0 until g!!.getChildCount()) {
                val found: TemperatureChartView? = findChart(g!!.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun makeText(text: String?, sp: Int, bold: Boolean, color: Int): TextView? {
        val tv: TextView = TextView(this)
        tv.setText(text)
        tv.setTextSize(sp.toFloat())
        tv.setTextColor(color)
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD)
        return tv
    }

    private fun roundStroke(color: Int, radius: Int, stroke: Int, width: Int): GradientDrawable? {
        val g: GradientDrawable = GradientDrawable()
        g.setColor(color)
        g.setCornerRadius(dp(radius).toFloat())
        g.setStroke(dp(width), stroke)
        return g
    }

    private fun dp(value: Int): Int {
        return Math.round(value * getResources()!!.getDisplayMetrics()!!.density)
    }

    private fun toast(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_LONG)!!.show()
    }

    private class TestFrame
    internal constructor(
        internal val camera: Double,
        internal val setpoint: Double,
        internal val probeK: Double,
        internal val probeT: Double,
        internal val heater: Double,
        internal val mode: String?,
        internal val autoRunning: Boolean,
        internal val program: String?,
        internal val stage: Int,
        internal val autoStatus: String?,
    )

    companion object {
        private val TEXT: Int = Color.rgb(21, 31, 47)
        private val MUTED: Int = Color.rgb(101, 116, 139)
        private val BORDER: Int = Color.rgb(220, 225, 232)
        private val CARD: Int = Color.WHITE
        private val GREEN: Int = Color.rgb(35, 151, 83)
        private val ORANGE: Int = Color.rgb(231, 138, 7)
        private val BLUE: Int = Color.rgb(31, 122, 210)
        private val RED: Int = Color.rgb(229, 40, 40)
        private val OFF: Int = Color.rgb(116, 129, 145)
        private val WARN_BG: Int = Color.rgb(255, 247, 232)
        private val CAMERA: Int = Color.rgb(9, 47, 73)
        private val SETPOINT: Int = Color.rgb(31, 122, 210)
        private val PROBE_K: Int = Color.rgb(35, 151, 83)
        private val PROBE_T: Int = Color.rgb(126, 87, 194)
        private val HEATER: Int = Color.rgb(231, 138, 7)
        private val LIVE_MS: Long = 10000L
        private val HISTORY_MS: Long = 24L * 60L * 60L * 1000L
        private val SESSION_SPLIT_MS: Long = 10L * 60L * 1000L
        private val CHANNEL_ID: String = "homesmoke_remote_alerts"
        private val REQ_NOTIFY: Int = 1401
        private val TEST_SCENARIOS: Array<String> =
            arrayOf<String>(
                "Полный цикл",
                "Нагрев камеры",
                "Стабилизация PID",
                "Auto-программа",
                "Щуп достигает цели",
                "Потеря и восстановление связи",
            )

        private fun round1(v: Double): Double {
            return Math.round(v * 10.0) / 10.0
        }

        private fun value(v: Double, suffix: String?): String? {
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) return "—"
            return String.format(Locale.getDefault(), "%.1f", v)!! + suffix!!
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
