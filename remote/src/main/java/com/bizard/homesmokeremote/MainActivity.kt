package com.bizard.homesmokeremote

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** HomeSmoke Remote — MQTT-only monitor/control with correlated controller ACK. */
open class MainActivity : ComponentActivity() {

    private var prefs: SharedPreferences? = null
    private var secrets: SecretStore? = null
    private var mqtt: MqttClient? = null
    private var historyStore: TelemetryHistoryStore? = null
    @Volatile private var connecting: Boolean = false
    @Volatile private var wantConnection: Boolean = false
    private var lastTelemetryAt: Long = 0
    private var deviceId: String? = "—"
    private var pendingId: String? = ""
    private var pendingLabel: String? = ""
    private var lastModeRaw: String? = "—"
    private var lastAutoRunning: Boolean = false
    private var lastCameraValue: Double = java.lang.Double.NaN
    private var lastSetpointValue: Double = java.lang.Double.NaN
    private var lastPowerValue: Double = java.lang.Double.NaN
    private var lastTrendColor: Int = MUTED
    private var showTechnicalEnabled: Boolean = false
    private var passwordVisible: Boolean = false
    private var graphVisible: Boolean = false
    private var graphSessionMode: Boolean = false
    private var graphWindowMs: Long = 3L * 60L * 60L * 1000L
    @Volatile private var graphSessionStartAt: Long = 0L
    @Volatile private var graphLastLiveAt: Long = 0L

    private val tempSamples: ArrayList<TempSample?> = ArrayList<TempSample?>()
    private val commandHistoryItems: ArrayList<String?> = ArrayList<String?>()
    private val graphRangeButtons: ArrayList<Button?> = ArrayList<Button?>()

    private var host: LinearLayout? = null
    private var monitorPage: LinearLayout? = null
    private var settingsPage: LinearLayout? = null
    private var graphPage: LinearLayout? = null
    private var ackFlow: LinearLayout? = null
    private var controllerCommandBlock: LinearLayout? = null
    private var advancedMqttBlock: LinearLayout? = null
    private var commandHistoryCard: LinearLayout? = null
    private var telemetryCamCard: LinearLayout? = null
    private var telemetryProbesRow: LinearLayout? = null
    private var telemetryStatsRow: LinearLayout? = null
    private var telemetryAutoCard: LinearLayout? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var mqttBadge: TextView? = null
    private var deviceBadge: TextView? = null
    private var mqttDot: TextView? = null
    private var deviceDot: TextView? = null
    private var brokerState: TextView? = null
    private var deviceState: TextView? = null
    private var brokerDetail: TextView? = null
    private var deviceDetail: TextView? = null
    private var systemState: TextView? = null
    private var camera: TextView? = null
    private var cameraSummary: TextView? = null
    private var tempTrend: TextView? = null
    private var k: TextView? = null
    private var t: TextView? = null
    private var power: TextView? = null
    private var mode: TextView? = null
    private var lastCommand: TextView? = null
    private var autoProgram: TextView? = null
    private var autoStage: TextView? = null
    private var autoStatus: TextView? = null
    private var autoChip: TextView? = null
    private var lastUpdate: TextView? = null
    private var commandState: TextView? = null
    private var commandHistory: TextView? = null
    private var controlAvailability: TextView? = null
    private var ackRemote: TextView? = null
    private var ackHome: TextView? = null
    private var ackController: TextView? = null
    private var graphSummary: TextView? = null
    private var graphPointInfo: TextView? = null
    private var heaterProgress: ProgressBar? = null
    private var graphChart: TemperatureChartView? = null
    private var back: Button? = null
    private var graphButton: Button? = null
    private var settings: Button? = null
    private var setButton: Button? = null
    private var stopButton: Button? = null
    private var disconnectButton: Button? = null
    private var passToggle: Button? = null
    private var setInput: EditText? = null
    private var broker: EditText? = null
    private var port: EditText? = null
    private var statusTopic: EditText? = null
    private var commandTopic: EditText? = null
    private var ackTopic: EditText? = null
    private var user: EditText? = null
    private var pass: EditText? = null
    private var tls: CheckBox? = null
    private var autoConnect: CheckBox? = null
    private var keepScreenOn: CheckBox? = null
    private var showTechnical: CheckBox? = null
    private var graphCamera: CheckBox? = null
    private var graphSetpoint: CheckBox? = null
    private var graphK: CheckBox? = null
    private var graphT: CheckBox? = null
    private val handler: Handler = Handler(Looper.getMainLooper())

    private val health: Runnable =
        object : Runnable {
            public override fun run() {
                val mq: Boolean = mqtt != null && mqtt!!.isConnected
                setBrokerUi(
                    mq,
                    if (mq) "Брокер подключён"
                    else (if (connecting) "Подключение к брокеру…" else "MQTT отключён"),
                )
                val fresh: Boolean = isTelemetryFresh
                val detail: String?
                if (lastTelemetryAt == 0L) detail = "Данные от коптильни не получены"
                else if (fresh) detail = "Коптильня онлайн · " + deviceId!!
                else detail = "Последние данные · " + relativeAge(lastTelemetryAt)!!
                setDeviceUi(fresh, detail)
                updateLastDataCaption()
                updateCameraSummaryAndTrend()
                applyTelemetryFreshness(fresh)
                if (graphVisible) refreshGraph()
                if (
                    wantConnection &&
                        !mq &&
                        !connecting &&
                        !broker!!.getText()!!.toString()!!.trim({ it <= ' ' }).isEmpty()
                )
                    connectMqtt(false)
                handler.postDelayed(this, 3000)
            }
        }

    private val isTelemetryFresh: Boolean
        get() {
            return isFreshAt(lastTelemetryAt)
        }

    protected override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        secrets = SecretStore(this)
        historyStore = TelemetryHistoryStore(this)
        val root: View? = buildRoot()
        setContentView(root)
        applyInsets(root)
        loadSettings()
        loadCommandHistory()
        applyUiPreferences()
        showMonitor()
        installModernComposeUi()
        wantConnection =
            autoConnect!!.isChecked() &&
                !broker!!.getText()!!.toString()!!.trim({ it <= ' ' }).isEmpty()
        if (wantConnection) connectMqtt(false)
        handler.postDelayed(health, 1500)
    }

    protected override fun onDestroy() {
        saveSettings()
        saveCommandHistory()
        wantConnection = false
        handler.removeCallbacks(health)
        disconnectInternal(false)
        if (historyStore != null) historyStore!!.close()
        super.onDestroy()
    }

    /** Compose is an overlay during the visual redesign; the existing View tree remains the compatibility layer. */
    private fun installModernComposeUi() {
        // Robolectric's API 23 shadow does not provide the window lifecycle hooks
        // required by ComposeView. Real Android 6 devices still receive the UI.
        if (isRobolectricRuntime()) return
        ModernComposeOverlay.install(this)
    }

    private fun isRobolectricRuntime(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    internal fun modernSnapshot(): ModernRemoteSnapshot {
        val page = if (graphVisible) ModernRemotePage.GRAPH
        else if (settings != null && settings!!.getVisibility() == View.GONE) ModernRemotePage.SETTINGS
        else ModernRemotePage.MONITOR
        return ModernRemoteSnapshot(
            page = page,
            mqttConnected = mqtt != null && mqtt!!.isConnected,
            brokerState = textOf(brokerState),
            brokerDetail = textOf(brokerDetail),
            deviceState = textOf(deviceState),
            deviceDetail = textOf(deviceDetail),
            camera = textOf(camera),
            cameraSummary = textOf(cameraSummary),
            trend = textOf(tempTrend),
            probeK = textOf(k),
            probeT = textOf(t),
            heater = textOf(power),
            mode = textOf(mode),
            autoProgram = textOf(autoProgram),
            autoStage = textOf(autoStage),
            autoStatus = textOf(autoStatus),
            lastCommand = textOf(lastCommand),
            commandState = textOf(commandState),
            commandHistory = textOf(commandHistory),
            ackRemote = textOf(ackRemote),
            ackHome = textOf(ackHome),
            ackController = textOf(ackController),
            lastUpdate = textOf(lastUpdate),
            controlAvailability = textOf(controlAvailability),
            graphSummary = textOf(graphSummary),
            graphPoint = textOf(graphPointInfo),
            testRunning = modernBooleanField("testRunning"),
            testScenario = modernScenarioField(),
            testScenarioIndex = modernScenarioIndex(),
            broker = textOf(broker),
            port = textOf(port),
            statusTopic = textOf(statusTopic),
            commandTopic = textOf(commandTopic),
            ackTopic = textOf(ackTopic),
            username = textOf(user),
            passwordConfigured = secrets?.get()?.isNotBlank() == true,
            tls = tls != null && tls!!.isChecked(),
            autoConnect = autoConnect != null && autoConnect!!.isChecked(),
            keepScreenOn = keepScreenOn != null && keepScreenOn!!.isChecked(),
            technicalData = showTechnical != null && showTechnical!!.isChecked(),
        )
    }

    internal fun modernGraphSamples(): List<TelemetryHistoryStore.Sample?> {
        val store = historyStore ?: return emptyList()
        val to = System.currentTimeMillis()
        val from = if (graphSessionMode) {
            if (graphSessionStartAt > 0) graphSessionStartAt else to - 60L * 60L * 1000L
        } else {
            to - graphWindowMs
        }
        return store.query(from, to, 900) ?: emptyList()
    }

    internal fun modernGraphRangeKey(): String {
        return if (graphSessionMode) "session" else graphWindowMs.toString()
    }

    internal fun modernSetGraphRange(windowMs: Long, session: Boolean) {
        graphSessionMode = session
        if (!session) graphWindowMs = windowMs
        saveSettings()
        if (graphVisible) refreshGraph()
    }

    internal fun modernShowMonitor() = showMonitor()
    internal fun modernShowGraph() = showGraph()
    internal fun modernShowSettings() = showSettings()
    internal fun modernConnect() {
        wantConnection = true
        connectMqtt(true)
    }
    internal fun modernDisconnect() {
        wantConnection = false
        disconnectInternal(true)
    }
    internal fun modernApplySetpoint(value: String) {
        setInput!!.setText(value)
        sendSetpoint()
    }
    internal fun modernStop() = confirmStop()
    internal fun modernStartTest() = modernInvokeGraph("startTestScenario")
    internal fun modernStopTest() = modernInvokeGraph("stopTestScenario", true)
    internal fun modernSetTechnical(enabled: Boolean) {
        showTechnical?.setChecked(enabled)
        applyUiPreferences()
        saveSettings()
    }
    internal fun modernSetTestScenario(index: Int) {
        try {
            val bounded = index.coerceIn(0, 5)
            val indexField = GraphUxActivity::class.java.getDeclaredField("testScenarioIndex")
            indexField.isAccessible = true
            indexField.setInt(this, bounded)
            val spinnerField = GraphUxActivity::class.java.getDeclaredField("testScenarioSpinner")
            spinnerField.isAccessible = true
            (spinnerField.get(this) as? android.widget.Spinner)?.setSelection(bounded)
            getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
                .edit()
                .putInt("test_scenario", bounded)
                .apply()
        } catch (_: Exception) {
            // The compact UI remains usable when the optional test controller is unavailable.
        }
    }
    internal fun modernSaveSettings(value: ModernSettingsValues) {
        broker!!.setText(value.broker)
        port!!.setText(value.port)
        statusTopic!!.setText(value.statusTopic)
        commandTopic!!.setText(value.commandTopic)
        ackTopic!!.setText(value.ackTopic)
        user!!.setText(value.username)
        if (value.password.isNotBlank()) pass!!.setText(value.password)
        tls!!.setChecked(value.tls)
        autoConnect!!.setChecked(value.autoConnect)
        keepScreenOn!!.setChecked(value.keepScreenOn)
        saveSettings()
        applyUiPreferences()
        if (value.autoConnect && value.broker.isNotBlank()) modernConnect()
    }

    private fun modernInvokeGraph(name: String, vararg args: Any) {
        try {
            val types = args.map { if (it is Boolean) Boolean::class.javaPrimitiveType else it.javaClass }.toTypedArray()
            val method = GraphUxActivity::class.java.getDeclaredMethod(name, *types)
            method.isAccessible = true
            method.invoke(this, *args)
        } catch (_: Exception) {
            // The monitor screen remains usable when launched outside the graph activity.
        }
    }

    private fun modernBooleanField(name: String): Boolean {
        return try {
            val field = GraphUxActivity::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.getBoolean(this)
        } catch (_: Exception) { false }
    }

    private fun modernScenarioField(): String {
        return try {
            val indexField = GraphUxActivity::class.java.getDeclaredField("testScenarioIndex")
            indexField.isAccessible = true
            val index = indexField.getInt(this)
            val scenarios = arrayOf("Полный цикл", "Нагрев камеры", "Стабилизация PID", "Auto-программа", "Щуп достигает цели", "Потеря связи")
            scenarios[index.coerceIn(scenarios.indices)]
        } catch (_: Exception) { "Полный цикл" }
    }

    private fun modernScenarioIndex(): Int {
        return try {
            val indexField = GraphUxActivity::class.java.getDeclaredField("testScenarioIndex")
            indexField.isAccessible = true
            indexField.getInt(this).coerceIn(0, 5)
        } catch (_: Exception) { 0 }
    }

    private fun textOf(view: TextView?): String = view?.text?.toString()?.trim().orEmpty().ifBlank { "—" }

    private fun buildRoot(): View? {
        val root: LinearLayout = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setBackgroundColor(BG)
        root.addView(buildBar(), LinearLayout.LayoutParams(-1, dp(60)))
        host = LinearLayout(this)
        host!!.setOrientation(LinearLayout.VERTICAL)
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        monitorPage = buildMonitor()
        graphPage = buildGraph()
        settingsPage = buildSettings()
        return root
    }

    private fun buildBar(): View? {
        val bar: LinearLayout = LinearLayout(this)
        bar.setGravity(Gravity.CENTER_VERTICAL)
        bar.setPadding(dp(8), 0, dp(6), 0)
        bar.setBackgroundColor(NAVY)

        back = iconButton("‹")
        back!!.setTextSize(34f)
        back!!.setVisibility(View.GONE)
        back!!.setOnClickListener({ view -> showMonitor() })
        bar.addView(back, LinearLayout.LayoutParams(dp(38), dp(44)))

        val titles: LinearLayout = LinearLayout(this)
        titles.setOrientation(LinearLayout.VERTICAL)
        titles.setGravity(Gravity.CENTER_VERTICAL)
        titles.setPadding(dp(2), 0, dp(3), 0)
        title = text("HomeSmoke Remote", 18, true, TEXT)
        title!!.setTextColor(Color.WHITE)
        title!!.setSingleLine(true)
        title!!.setEllipsize(TextUtils.TruncateAt.END)
        subtitle = text("Удалённое управление", 11, false, MUTED)
        subtitle!!.setTextColor(Color.rgb(211, 222, 232))
        subtitle!!.setSingleLine(true)
        subtitle!!.setEllipsize(TextUtils.TruncateAt.END)
        titles.addView(title)
        titles.addView(subtitle)
        bar.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))

        mqttBadge = badge("MQTT")
        deviceBadge = badge("SMOKE")
        bar.addView(mqttBadge, wrapMargin(2, 0, 2, 0))
        bar.addView(deviceBadge, wrapMargin(2, 0, 2, 0))

        graphButton = iconButton("↗")
        graphButton!!.setTextSize(22f)
        graphButton!!.setContentDescription("График температуры")
        graphButton!!.setOnClickListener({ view -> showGraph() })
        bar.addView(graphButton, LinearLayout.LayoutParams(dp(34), dp(42)))

        settings = iconButton("⚙")
        settings!!.setTextSize(20f)
        settings!!.setOnClickListener({ view -> showSettings() })
        bar.addView(settings, LinearLayout.LayoutParams(dp(36), dp(42)))
        return bar
    }

    private fun buildMonitor(): LinearLayout? {
        val p: LinearLayout? = page()

        val healthCard: LinearLayout? = card()
        val healthHeader: LinearLayout = LinearLayout(this)
        healthHeader.setGravity(Gravity.CENTER_VERTICAL)
        healthHeader.addView(sectionTitle("Связь"), LinearLayout.LayoutParams(0, -2, 1f))
        systemState = statusChip("ОФЛАЙН", OFF)
        healthHeader.addView(systemState)
        healthCard!!.addView(healthHeader)
        brokerState = statusRow(healthCard, "MQTT", "Отключён", RED, true)
        deviceState = statusRow(healthCard, "Коптильня", "Нет данных", ORANGE, false)
        brokerDetail = smallDetail("MQTT отключён")
        deviceDetail = smallDetail("Телеметрия ещё не поступала")
        healthCard!!.addView(brokerDetail)
        healthCard!!.addView(deviceDetail)
        p!!.addView(healthCard, margin(8, 8, 8, 4))

        telemetryCamCard = card()
        val cam: LinearLayout? = telemetryCamCard
        cam!!.addView(label("Камера"))
        camera = center("— °C", 44, true, TEXT)
        camera!!.setPadding(0, dp(2), 0, 0)
        cam!!.addView(camera)
        cameraSummary = center("Уставка — °C", 14, true, BLUE_DARK)
        cam!!.addView(cameraSummary)
        tempTrend = center("Тренд —", 11, false, MUTED)
        tempTrend!!.setPadding(0, dp(2), 0, 0)
        cam!!.addView(tempTrend)
        p!!.addView(cam, margin(8, 4, 8, 4))

        telemetryProbesRow = LinearLayout(this)
        val probes: LinearLayout? = telemetryProbesRow
        probes!!.setOrientation(LinearLayout.HORIZONTAL)
        val kc: LinearLayout? = metricCard("Щуп K")
        k = center("— °C", 24, true, TEXT)
        kc!!.addView(k)
        val tc: LinearLayout? = metricCard("Щуп T")
        t = center("— °C", 24, true, TEXT)
        tc!!.addView(t)
        probes!!.addView(kc, half(8, 4))
        probes!!.addView(tc, half(4, 8))
        p!!.addView(probes)

        telemetryStatsRow = LinearLayout(this)
        val stats: LinearLayout? = telemetryStatsRow
        stats!!.setOrientation(LinearLayout.HORIZONTAL)
        val heater: LinearLayout? = metricCard("ТЭН")
        power = center("— %", 22, true, ORANGE)
        heater!!.addView(power)
        heaterProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        heaterProgress!!.setMax(100)
        heaterProgress!!.setProgress(0)
        if (Build.VERSION.SDK_INT >= 21)
            heaterProgress!!.setProgressTintList(ColorStateList.valueOf(ORANGE))
        val hp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(5))
        hp.setMargins(dp(8), dp(4), dp(8), 0)
        heater!!.addView(heaterProgress, hp)
        val modeCard: LinearLayout? = metricCard("Режим")
        mode = center("—", 21, true, TEXT)
        mode!!.setSingleLine(true)
        mode!!.setEllipsize(TextUtils.TruncateAt.END)
        modeCard!!.addView(mode)
        stats!!.addView(heater, half(8, 4))
        stats!!.addView(modeCard, half(4, 8))
        p!!.addView(stats)

        telemetryAutoCard = card()
        val ac: LinearLayout? = telemetryAutoCard
        val autoHeader: LinearLayout = LinearLayout(this)
        autoHeader.setGravity(Gravity.CENTER_VERTICAL)
        autoHeader.addView(sectionTitle("Auto"), LinearLayout.LayoutParams(0, -2, 1f))
        autoChip = statusChip("ВЫКЛ", OFF)
        autoHeader.addView(autoChip)
        ac!!.addView(autoHeader)
        autoProgram = info(ac, "Программа", "—")
        autoStage = info(ac, "Этап", "—")
        (autoProgram!!.getParent() as View).setVisibility(View.GONE)
        (autoStage!!.getParent() as View).setVisibility(View.GONE)
        autoStatus = text("Программа не запущена", 13, false, MUTED)
        autoStatus!!.setPadding(0, dp(5), 0, 0)
        ac!!.addView(autoStatus)
        p!!.addView(ac, margin(8, 4, 8, 4))

        val ctrl: LinearLayout? = card()
        ctrl!!.addView(sectionTitle("Удалённое управление"))
        controlAvailability = text("Управление недоступно · нет свежих данных", 12, true, MUTED)
        controlAvailability!!.setPadding(dp(9), dp(6), dp(9), dp(6))
        controlAvailability!!.setBackground(roundStroke(WARN_BG, 10, ORANGE, 1))
        val avp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        avp.setMargins(0, dp(6), 0, dp(6))
        ctrl!!.addView(controlAvailability, avp)

        val setRow: LinearLayout = LinearLayout(this)
        setRow.setGravity(Gravity.CENTER_VERTICAL)
        setInput = field("Уставка 0…100 °C", InputType.TYPE_CLASS_NUMBER)
        val ip: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        ip.setMargins(0, 0, dp(7), 0)
        setRow.addView(setInput, ip)
        setButton = action("Применить", BLUE)
        setButton!!.setTextSize(14f)
        setButton!!.setOnClickListener({ view -> sendSetpoint() })
        setRow.addView(setButton, LinearLayout.LayoutParams(0, dp(46), 0.88f))
        ctrl!!.addView(setRow, LinearLayout.LayoutParams(-1, -2))

        stopButton = action("STOP · выключить нагрев", RED)
        stopButton!!.setTextSize(15f)
        stopButton!!.setOnClickListener({ view -> confirmStop() })
        val sp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(48))
        sp.setMargins(0, dp(7), 0, 0)
        ctrl!!.addView(stopButton, sp)

        ackFlow = buildAckFlow()
        ackFlow!!.setVisibility(View.GONE)
        val afp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        afp.setMargins(0, dp(8), 0, 0)
        ctrl!!.addView(ackFlow, afp)

        commandState = text("Команды ещё не отправлялись", 12, false, MUTED)
        commandState!!.setPadding(dp(9), dp(7), dp(9), dp(7))
        commandState!!.setBackground(roundStroke(INFO_BG, 10, BORDER, 1))
        val cp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        cp.setMargins(0, dp(7), 0, 0)
        ctrl!!.addView(commandState, cp)
        p!!.addView(ctrl, margin(8, 4, 8, 4))

        commandHistoryCard = card()
        commandHistoryCard!!.addView(label("История команд Remote"))
        commandHistory = text("Команд Remote ещё не было", 12, false, MUTED)
        commandHistory!!.setPadding(0, dp(5), 0, dp(4))
        commandHistory!!.setLineSpacing(0f, 1.08f)
        commandHistoryCard!!.addView(commandHistory)

        controllerCommandBlock = LinearLayout(this)
        controllerCommandBlock!!.setOrientation(LinearLayout.VERTICAL)
        val controllerLabel: TextView? = text("Последняя команда контроллера", 11, false, MUTED)
        controllerLabel!!.setPadding(0, dp(4), 0, 0)
        controllerCommandBlock!!.addView(controllerLabel)
        lastCommand = text("—", 14, true, TEXT)
        lastCommand!!.setPadding(0, dp(2), 0, 0)
        lastCommand!!.setMaxLines(2)
        lastCommand!!.setEllipsize(TextUtils.TruncateAt.END)
        controllerCommandBlock!!.addView(lastCommand)
        commandHistoryCard!!.addView(controllerCommandBlock)
        p!!.addView(commandHistoryCard, margin(8, 4, 8, 3))

        lastUpdate = center("Данных ещё нет", 11, false, MUTED)
        p!!.addView(lastUpdate, margin(8, 2, 8, 16))
        return p
    }

    private fun buildGraph(): LinearLayout? {
        val p: LinearLayout? = page()

        val intro: LinearLayout? = card()
        intro!!.addView(sectionTitle("График температуры"))
        val hint: TextView? =
            text(
                "Remote сохраняет только свежую телеметрию локально до 24 часов. Retained/устаревшие данные в историю не добавляются.",
                11,
                false,
                MUTED,
            )
        hint!!.setPadding(0, dp(3), 0, 0)
        intro!!.addView(hint)
        p!!.addView(intro, margin(8, 8, 8, 4))

        val rangeCard: LinearLayout? = card()
        rangeCard!!.addView(label("Период"))
        val ranges: LinearLayout = LinearLayout(this)
        ranges.setGravity(Gravity.CENTER_VERTICAL)
        ranges.setPadding(0, dp(6), 0, 0)
        addGraphRangeButton(ranges, "1ч", 1L * 60L * 60L * 1000L, false)
        addGraphRangeButton(ranges, "3ч", 3L * 60L * 60L * 1000L, false)
        addGraphRangeButton(ranges, "6ч", 6L * 60L * 60L * 1000L, false)
        addGraphRangeButton(ranges, "12ч", 12L * 60L * 60L * 1000L, false)
        addGraphRangeButton(ranges, "24ч", 24L * 60L * 60L * 1000L, false)
        addGraphRangeButton(ranges, "Сеанс", 0L, true)
        rangeCard!!.addView(ranges, LinearLayout.LayoutParams(-1, -2))
        p!!.addView(rangeCard, margin(8, 4, 8, 4))

        val chartCard: LinearLayout? = card()
        graphChart = TemperatureChartView(this)
        graphChart!!.setOnSelectionListener({ sample ->
            if (graphPointInfo != null) graphPointInfo!!.setText(graphPointText(sample))
        })
        chartCard!!.addView(graphChart, LinearLayout.LayoutParams(-1, dp(330)))
        graphSummary = text("Свежих данных пока нет", 11, false, MUTED)
        graphSummary!!.setGravity(Gravity.CENTER)
        graphSummary!!.setPadding(0, dp(5), 0, 0)
        chartCard!!.addView(graphSummary)
        p!!.addView(chartCard, margin(8, 4, 8, 4))

        val series: LinearLayout? = card()
        series!!.addView(label("Линии графика"))
        val row1: LinearLayout = LinearLayout(this)
        val row2: LinearLayout = LinearLayout(this)
        row1.setOrientation(LinearLayout.HORIZONTAL)
        row2.setOrientation(LinearLayout.HORIZONTAL)
        graphCamera = check("Камера")
        graphSetpoint = check("Уставка")
        graphK = check("Щуп K")
        graphT = check("Щуп T")
        graphCamera!!.setChecked(true)
        graphSetpoint!!.setChecked(true)
        graphK!!.setChecked(true)
        graphT!!.setChecked(true)
        row1.addView(graphCamera, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(graphSetpoint, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(graphK, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(graphT, LinearLayout.LayoutParams(0, -2, 1f))
        series!!.addView(row1)
        series!!.addView(row2)
        val listener: android.widget.CompoundButton.OnCheckedChangeListener? =
            android.widget.CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                updateGraphSeries()
                saveSettings()
            }
        graphCamera!!.setOnCheckedChangeListener(listener)
        graphSetpoint!!.setOnCheckedChangeListener(listener)
        graphK!!.setOnCheckedChangeListener(listener)
        graphT!!.setOnCheckedChangeListener(listener)
        val heaterHint: TextView? =
            text(
                "Мощность ТЭНа отображается отдельной полосой 0…100 % под температурным графиком.",
                11,
                false,
                MUTED,
            )
        heaterHint!!.setPadding(0, dp(2), 0, 0)
        series!!.addView(heaterHint)
        p!!.addView(series, margin(8, 4, 8, 4))

        val point: LinearLayout? = card()
        point!!.addView(label("Точка графика"))
        graphPointInfo = text("Коснитесь графика, чтобы увидеть точные значения.", 12, false, MUTED)
        graphPointInfo!!.setPadding(0, dp(4), 0, 0)
        graphPointInfo!!.setLineSpacing(0f, 1.05f)
        point!!.addView(graphPointInfo)
        p!!.addView(point, margin(8, 4, 8, 16))
        updateGraphRangeButtons()
        return p
    }

    private fun addGraphRangeButton(
        row: LinearLayout?,
        label: String?,
        window: Long,
        session: Boolean,
    ) {
        val b: Button? = action(label, OFF)
        b!!.setTextSize(10f)
        b!!.setTag(if (session) "session" else (window).toString())
        b!!.setOnClickListener({ v ->
            graphSessionMode = session
            if (!session) graphWindowMs = window
            updateGraphRangeButtons()
            saveSettings()
            refreshGraph()
        })
        val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        lp.setMargins(dp(2), 0, dp(2), 0)
        row!!.addView(b, lp)
        graphRangeButtons.add(b)
    }

    private fun updateGraphRangeButtons() {
        for (b: Button? in graphRangeButtons) {
            val tag: Any? = b!!.getTag()
            val active: Boolean
            if ("session" == tag) active = graphSessionMode
            else {
                var value: Long
                try {
                    value = java.lang.Long.parseLong((tag).toString())
                } catch (e: Exception) {
                    value = -1
                }

                active = !graphSessionMode && value == graphWindowMs
            }
            b!!.setTextColor(if (active) Color.WHITE else TEXT)
            b!!.setBackground(round(if (active) BLUE else Color.rgb(235, 239, 244), 11))
        }
    }

    private fun updateGraphSeries() {
        if (graphChart == null || graphCamera == null) return
        graphChart!!.setSeries(
            graphCamera!!.isChecked(),
            graphSetpoint!!.isChecked(),
            graphK!!.isChecked(),
            graphT!!.isChecked(),
        )
    }

    private fun refreshGraph() {
        if (historyStore == null || graphChart == null) return
        val now: Long = System.currentTimeMillis()
        var from: Long =
            if (graphSessionMode)
                (if (graphSessionStartAt > 0) graphSessionStartAt else now - graphWindowMs)
            else now - graphWindowMs
        if (graphSessionMode && graphSessionStartAt <= 0) from = now - 60L * 60L * 1000L
        val samples: List<TelemetryHistoryStore.Sample?>? = historyStore!!.query(from, now, 800)
        graphChart!!.setData(samples)
        updateGraphSeries()
        updateGraphRangeButtons()
        if (samples!!.isEmpty()) {
            graphSummary!!.setText(
                if (graphSessionMode) "В текущем сеансе свежих данных пока нет"
                else "Свежих данных за выбранный период нет"
            )
            graphPointInfo!!.setText("Коснитесь графика, чтобы увидеть точные значения.")
        } else {
            val first: TelemetryHistoryStore.Sample? = samples!!.get(0)
            val last: TelemetryHistoryStore.Sample? = samples!!.get(samples!!.size - 1)
            val firstTime: String? =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(first!!.ts))
            val lastTime: String? =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last!!.ts))
            graphSummary!!.setText(
                samples!!.size.toString() +
                    " точек на графике · " +
                    firstTime +
                    "—" +
                    lastTime +
                    " · последние " +
                    relativeAge(last!!.ts)
            )
        }
    }

    private fun graphPointText(s: TelemetryHistoryStore.Sample?): String? {
        val time: String? =
            SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(s!!.ts))
        return time +
            "\nКамера " +
            graphValue(s!!.camera, " °C") +
            " · Уставка " +
            graphValue(s!!.setpoint, " °C") +
            "\nЩуп K " +
            graphValue(s!!.probeK, " °C") +
            " · Щуп T " +
            graphValue(s!!.probeT, " °C") +
            " · ТЭН " +
            graphValue(s!!.heater, " %")
    }

    private fun buildAckFlow(): LinearLayout? {
        val row: LinearLayout = LinearLayout(this)
        row.setGravity(Gravity.CENTER_VERTICAL)
        ackRemote = ackPill("Remote")
        ackHome = ackPill("HomeSmoke")
        ackController = ackPill("Arduino")
        row.addView(ackRemote, LinearLayout.LayoutParams(0, dp(30), 1f))
        row.addView(arrow("›"))
        row.addView(ackHome, LinearLayout.LayoutParams(0, dp(30), 1.25f))
        row.addView(arrow("›"))
        row.addView(ackController, LinearLayout.LayoutParams(0, dp(30), 1f))
        setAckProgress(0, false)
        return row
    }

    private fun ackPill(text: String?): TextView? {
        val t: TextView? = center(text, 10, true, Color.WHITE)
        t!!.setSingleLine(true)
        t!!.setPadding(dp(4), 0, dp(4), 0)
        t!!.setBackground(round(OFF, 10))
        return t
    }

    private fun arrow(s: String?): TextView? {
        val t: TextView? = center(s, 19, true, MUTED)
        t!!.setPadding(dp(3), 0, dp(3), 0)
        return t
    }

    private fun buildSettings(): LinearLayout? {
        val p: LinearLayout? = page()

        val intro: LinearLayout? = card()
        intro!!.addView(sectionTitle("MQTT подключение"))
        val versionText: TextView? = text("HomeSmoke Remote 2.0.13 · Android 5+", 12, false, MUTED)
        versionText!!.setPadding(0, dp(2), 0, 0)
        intro!!.addView(versionText)
        p!!.addView(intro, margin(8, 8, 8, 4))

        val form: LinearLayout? = card()
        broker = labeledField(form, "Broker / IP", "Адрес MQTT брокера", InputType.TYPE_CLASS_TEXT)
        port = labeledField(form, "Port", "1883", InputType.TYPE_CLASS_NUMBER)

        advancedMqttBlock = LinearLayout(this)
        advancedMqttBlock!!.setOrientation(LinearLayout.VERTICAL)
        statusTopic =
            labeledField(
                advancedMqttBlock,
                "Status topic",
                "homesmoke/status",
                InputType.TYPE_CLASS_TEXT,
            )
        commandTopic =
            labeledField(
                advancedMqttBlock,
                "Command topic",
                "homesmoke/cmd",
                InputType.TYPE_CLASS_TEXT,
            )
        ackTopic =
            labeledField(advancedMqttBlock, "ACK topic", "homesmoke/ack", InputType.TYPE_CLASS_TEXT)
        form!!.addView(advancedMqttBlock)

        user = labeledField(form, "Логин", "Необязательно", InputType.TYPE_CLASS_TEXT)

        val passLabel: TextView? = text("Пароль", 12, true, MUTED)
        passLabel!!.setPadding(0, dp(6), 0, dp(4))
        form!!.addView(passLabel)
        val passRow: LinearLayout = LinearLayout(this)
        passRow.setGravity(Gravity.CENTER_VERTICAL)
        pass =
            field(
                "Необязательно",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            )
        pass!!.setTransformationMethod(PasswordTransformationMethod.getInstance())
        val pp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        pp.setMargins(0, 0, dp(7), 0)
        passRow.addView(pass, pp)
        passToggle = action("Показать", OFF)
        passToggle!!.setTextSize(11f)
        passToggle!!.setOnClickListener({ view -> setPasswordVisible(!passwordVisible) })
        passRow.addView(passToggle, LinearLayout.LayoutParams(dp(92), dp(46)))
        form!!.addView(passRow)

        tls = check("Использовать TLS")
        autoConnect = check("Автоподключение и переподключение")
        form!!.addView(tls, checkParams())
        form!!.addView(autoConnect, checkParams())
        p!!.addView(form, margin(8, 4, 8, 4))

        val ui: LinearLayout? = card()
        ui!!.addView(sectionTitle("Интерфейс"))
        keepScreenOn = check("Не выключать экран при открытом Remote")
        showTechnical = check("Показывать технические данные")
        keepScreenOn!!.setOnCheckedChangeListener({ buttonView, isChecked -> applyUiPreferences() })
        showTechnical!!.setOnCheckedChangeListener({ buttonView, isChecked ->
            showTechnicalEnabled = isChecked
            applyUiPreferences()
        })
        ui!!.addView(keepScreenOn, checkParams())
        ui!!.addView(showTechnical, checkParams())
        val uiHint: TextView? =
            text(
                "Технические данные включают MQTT topics, подробности соединения/устройства и последнюю команду контроллера.",
                11,
                false,
                MUTED,
            )
        uiHint!!.setPadding(0, dp(2), 0, 0)
        ui!!.addView(uiHint)
        p!!.addView(ui, margin(8, 4, 8, 4))

        val c: Button? = action("Сохранить и подключить", GREEN)
        c!!.setOnClickListener({ view ->
            saveSettings()
            applyUiPreferences()
            wantConnection = true
            connectMqtt(true)
        })
        p!!.addView(c, buttonMargin(8, 6, 8, 3))

        disconnectButton = action("Отключить MQTT", OFF)
        disconnectButton!!.setOnClickListener({ view ->
            wantConnection = false
            disconnectInternal(true)
        })
        p!!.addView(disconnectButton, buttonMargin(8, 3, 8, 6))

        val sec: String =
            if (secrets!!.isEncrypted) "Пароль MQTT хранится через Android Keystore."
            else
                "Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN."
        val n: TextView? =
            text(
                sec.toString() +
                    " Команда температуры передаётся только целым значением 0…100 °C — в соответствии с текущим подтверждённым протоколом.",
                11,
                false,
                MUTED,
            )
        n!!.setPadding(dp(12), dp(6), dp(12), dp(16))
        p!!.addView(n)
        return p
    }

    private fun loadSettings() {
        broker!!.setText(prefs!!.getString("broker", ""))
        port!!.setText(prefs!!.getString("port", "1883"))
        statusTopic!!.setText(prefs!!.getString("status_topic", "homesmoke/status"))
        commandTopic!!.setText(prefs!!.getString("command_topic", "homesmoke/cmd"))
        ackTopic!!.setText(prefs!!.getString("ack_topic", "homesmoke/ack"))
        user!!.setText(prefs!!.getString("user", ""))
        pass!!.setText(secrets!!.get())
        setPasswordVisible(false)
        tls!!.setChecked(prefs!!.getBoolean("tls", false))
        autoConnect!!.setChecked(prefs!!.getBoolean("auto", true))
        keepScreenOn!!.setChecked(prefs!!.getBoolean("keep_screen_on", false))
        showTechnical!!.setChecked(prefs!!.getBoolean("show_technical", false))
        showTechnicalEnabled = showTechnical!!.isChecked()
        graphWindowMs = prefs!!.getLong("graph_window_ms", 3L * 60L * 60L * 1000L)
        graphSessionMode = prefs!!.getBoolean("graph_session_mode", false)
        graphCamera!!.setChecked(prefs!!.getBoolean("graph_camera", true))
        graphSetpoint!!.setChecked(prefs!!.getBoolean("graph_setpoint", true))
        graphK!!.setChecked(prefs!!.getBoolean("graph_k", true))
        graphT!!.setChecked(prefs!!.getBoolean("graph_t", true))
        updateGraphSeries()
        updateGraphRangeButtons()
    }

    private fun saveSettings() {
        val e: SharedPreferences.Editor? =
            prefs!!
                .edit()!!
                .putString("broker", s(broker))!!
                .putString("port", s(port))!!
                .putString("status_topic", s(statusTopic))!!
                .putString("command_topic", s(commandTopic))!!
                .putString("ack_topic", s(ackTopic))!!
                .putString("user", user!!.getText()!!.toString())!!
                .putBoolean("tls", tls!!.isChecked())!!
                .putBoolean("auto", autoConnect!!.isChecked())!!
                .putBoolean("keep_screen_on", keepScreenOn!!.isChecked())!!
                .putBoolean("show_technical", showTechnical!!.isChecked())!!
                .putLong("graph_window_ms", graphWindowMs)!!
                .putBoolean("graph_session_mode", graphSessionMode)
        if (graphCamera != null)
            e!!
                .putBoolean("graph_camera", graphCamera!!.isChecked())!!
                .putBoolean("graph_setpoint", graphSetpoint!!.isChecked())!!
                .putBoolean("graph_k", graphK!!.isChecked())!!
                .putBoolean("graph_t", graphT!!.isChecked())
        e!!.apply()
        try {
            secrets!!.put(pass!!.getText()!!.toString())
        } catch (ex: Exception) {
            toast("Не удалось сохранить пароль защищённо")
        }
    }

    private fun setPasswordVisible(visible: Boolean) {
        if (pass == null) return
        passwordVisible = visible
        val pos: Int = pass!!.getSelectionStart()
        pass!!.setTransformationMethod(
            if (visible) null else PasswordTransformationMethod.getInstance()
        )
        if (passToggle != null) passToggle!!.setText(if (visible) "Скрыть" else "Показать")
        val len: Int = if (pass!!.getText() == null) 0 else pass!!.getText()!!.length
        pass!!.setSelection(Math.min(Math.max(pos, 0), len))
    }

    private fun applyUiPreferences() {
        if (keepScreenOn != null && keepScreenOn!!.isChecked())
            getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else getWindow()!!.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showTechnicalEnabled = showTechnical != null && showTechnical!!.isChecked()
        val v: Int = if (showTechnicalEnabled) View.VISIBLE else View.GONE
        if (brokerDetail != null) brokerDetail!!.setVisibility(v)
        if (deviceDetail != null) deviceDetail!!.setVisibility(v)
        if (controllerCommandBlock != null) controllerCommandBlock!!.setVisibility(v)
        if (advancedMqttBlock != null) advancedMqttBlock!!.setVisibility(v)
        if (commandHistoryCard != null)
            commandHistoryCard!!.setVisibility(
                if ((showTechnicalEnabled || !commandHistoryItems.isEmpty())) View.VISIBLE
                else View.GONE
            )
    }

    private fun connectMqtt(force: Boolean) {
        if (connecting) return
        saveSettings()
        val h: String? = s(broker)
        if (h!!.isEmpty()) {
            if (force) toast("Укажите MQTT broker")
            return
        }
        val po: Int
        try {
            po = Integer.parseInt(s(port))
        } catch (e: Exception) {
            if (force) toast("Неверный port")
            return
        }

        val st: String? = topic(s(statusTopic), "homesmoke/status")
        val at: String? = topic(s(ackTopic), "homesmoke/ack")
        disconnectInternal(false)
        connecting = true
        setBrokerUi(false, "Подключение к брокеру…")
        val c: MqttClient =
            MqttClient(h, po, tls!!.isChecked(), user!!.getText()!!.toString(), secrets!!.get())
        mqtt = c
        c.setMessageListener(
            MqttClient.MessageListener { topic, payload -> message(topic, payload) }
        )
        Thread(
                {
                    try {
                        c.connect()
                        c.subscribe(st)
                        c.subscribe(at)
                        connecting = false
                        runOnUiThread({
                            setBrokerUi(true, "Брокер подключён · " + h.toString() + ":" + po)
                        })
                    } catch (e: Exception) {
                        c.close()
                        if (mqtt == c) mqtt = null
                        connecting = false
                        runOnUiThread({ setBrokerUi(false, "MQTT ошибка: " + safe(e)!!) })
                    }
                },
                "HomeSmokeRemote-connect",
            )
            .start()
    }

    private fun message(topic: String?, payload: String?) {
        val st: String? = prefs!!.getString("status_topic", "homesmoke/status")
        val at: String? = prefs!!.getString("ack_topic", "homesmoke/ack")
        if (topic == st) status(payload) else if (topic == at) ack(payload)
    }

    private fun status(payload: String?) {
        try {
            val o: JSONObject = JSONObject(payload)
            val cam: String? = o.optString("temp_ds", "—")
            val pk: String? = o.optString("temp_tip_k", "—")
            val pt: String? = o.optString("temp_tip_t", "—")
            val sp: String? = o.optString("temp_k", "—")
            val pw: String? = o.optString("heater_power", "—")
            val md: String? = o.optString("mode", "—")
            val lc: String? = o.optString("last_command", o.optString("status", "—"))
            val ap: String? = o.optString("android_auto_program", "—")
            val `as`: String? = o.optString("android_auto_status", "Auto выключено")
            val stage: Int = o.optInt("android_auto_stage", 0)
            val ar: Boolean = o.optBoolean("android_auto_running", false)
            val did: String? = o.optString("device_id", deviceId)
            val ts: Long = normalizeTelemetryTs(o.optLong("ts", 0L))
            val camValue: Double = parseNumber(cam)
            val spValue: Double = parseNumber(sp)
            val powerValue: Double = parseNumber(pw)
            val pkValue: Double = parseNumber(pk)
            val ptValue: Double = parseNumber(pt)
            val fresh: Boolean = isFreshAt(ts)
            if (fresh && historyStore != null) {
                if (graphLastLiveAt <= 0 || ts - graphLastLiveAt > GRAPH_SESSION_GAP_MS)
                    graphSessionStartAt = ts
                graphLastLiveAt = ts
                historyStore!!.addFresh(
                    TelemetryHistoryStore.Sample(
                        ts,
                        camValue,
                        spValue,
                        pkValue,
                        ptValue,
                        powerValue,
                    )
                )
            }
            lastTelemetryAt = ts
            deviceId = did
            runOnUiThread({
                lastCameraValue = camValue
                lastSetpointValue = spValue
                lastModeRaw = md
                lastAutoRunning = ar
                camera!!.setText(deg(cam))
                k!!.setText(deg(pk))
                t!!.setText(deg(pt))
                lastPowerValue = powerValue
                updateHeaterUi(powerValue)
                updateModeUi(md)
                lastCommand!!.setText(lc)
                updateAutoUi(ar, ap, stage, `as`)
                addTempSample(camValue, ts)
                updateCameraSummaryAndTrend()
                updateLastDataCaption()
                setDeviceUi(
                    fresh,
                    if (fresh) "Коптильня онлайн · " + did!!
                    else "Последние данные · " + relativeAge(ts)!!,
                )
                applyTelemetryFreshness(fresh)
                if (graphVisible) refreshGraph()
            })
        } catch (ignored: Exception) {}
    }

    private fun ack(payload: String?) {
        try {
            val o: JSONObject = JSONObject(payload)
            val id: String? = o.optString("id", "")
            val ok: Boolean = o.optBoolean("ok", false)
            val state: String? = o.optString("state", o.optString("message", ""))
            val value: String? = if (o.has("value")) o.optString("value", "") else ""
            runOnUiThread({
                if (!pendingId!!.isEmpty() && !id!!.isEmpty() && pendingId != id)
                    return@runOnUiThread
                if ("accepted_waiting_controller" == state) {
                    setAckProgress(2, false)
                    setCommandUi("HomeSmoke принял команду · ожидается Arduino", 1)
                    return@runOnUiThread
                }
                val label: String? = pendingLabel
                pendingId = ""
                pendingLabel = ""
                if (ok && "applied" == state) {
                    setAckProgress(3, false)
                    setCommandUi("✓ Arduino применила уставку " + value + " °C", 2)
                    if (!label!!.isEmpty()) addCommandHistory(label, "✓ подтверждено")
                } else if (ok && "stop_sent" == state) {
                    setAckProgress(3, true)
                    setCommandUi("✓ HomeSmoke передал STOP контроллеру", 2)
                    if (!label!!.isEmpty()) addCommandHistory(label, "✓ передано")
                } else {
                    setAckError(state)
                    val translated: String? = translateState(state)
                    setCommandUi("Не выполнено · " + translated!!, 3)
                    if (!label!!.isEmpty()) addCommandHistory(label, "✕ " + translated!!)
                }
            })
        } catch (ignored: Exception) {}
    }

    private fun sendSetpoint() {
        val raw: String? = s(setInput)
        if (raw!!.isEmpty()) {
            toast("Введите температуру")
            return
        }
        val v: Double
        try {
            v = java.lang.Double.parseDouble(raw!!.replace(',', '.'))
        } catch (e: Exception) {
            toast("Неверное значение")
            return
        }

        if (v < 0 || v > 100 || Math.abs(v - Math.rint(v)) > .000001) {
            toast("Нужно целое число 0…100 °C")
            return
        }
        if (!isTelemetryFresh) {
            toast("Нет свежей телеметрии от коптильни")
            return
        }
        val c: MqttClient? = mqtt
        if (c == null || !c!!.isConnected) {
            toast("MQTT не подключён")
            return
        }
        try {
            val target: Int = Math.rint(v).toInt()
            pendingId = UUID.randomUUID()!!.toString()
            pendingLabel = "Уставка " + target + " °C"
            val o: JSONObject = JSONObject()
            o.put("v", 2)
            o.put("id", pendingId)
            o.put("cmd", "set_temp")
            o.put("value", target)
            o.put("ts", System.currentTimeMillis())
            c!!.publish(topic(s(commandTopic), "homesmoke/cmd"), o.toString(), false)
            setAckProgress(1, false)
            setCommandUi("Remote отправил команду · ожидается HomeSmoke", 1)
        } catch (e: Exception) {
            pendingId = ""
            pendingLabel = ""
            setAckError("")
            toast("Ошибка MQTT: " + safe(e)!!)
        }
    }

    private fun confirmStop() {
        if (!isTelemetryFresh) {
            toast("Нет свежей телеметрии от коптильни")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Удалённый STOP")!!
            .setMessage("Выключить нагрев на коптильне?")!!
            .setPositiveButton("STOP", { d, w -> sendStop() })!!
            .setNegativeButton("Отмена", null)!!
            .show()
    }

    private fun sendStop() {
        val c: MqttClient? = mqtt
        if (c == null || !c!!.isConnected) {
            toast("MQTT не подключён")
            return
        }
        try {
            pendingId = UUID.randomUUID()!!.toString()
            pendingLabel = "STOP"
            val o: JSONObject = JSONObject()
            o.put("v", 2)
            o.put("id", pendingId)
            o.put("cmd", "stop")
            o.put("ts", System.currentTimeMillis())
            c!!.publish(topic(s(commandTopic), "homesmoke/cmd"), o.toString(), false)
            setAckProgress(1, false)
            setCommandUi("Remote отправил STOP · ожидается HomeSmoke", 1)
        } catch (e: Exception) {
            pendingId = ""
            pendingLabel = ""
            setAckError("")
            toast("Ошибка MQTT: " + safe(e)!!)
        }
    }

    private fun setAckProgress(stage: Int, controllerSentOnly: Boolean) {
        if (ackFlow == null) return
        if (stage <= 0) {
            ackFlow!!.setVisibility(View.GONE)
            setAckStage(ackRemote, "Remote", OFF)
            setAckStage(ackHome, "HomeSmoke", OFF)
            setAckStage(ackController, "Arduino", OFF)
            return
        }
        ackFlow!!.setVisibility(View.VISIBLE)
        setAckStage(ackRemote, "Remote ✓", GREEN)
        setAckStage(
            ackHome,
            if (stage >= 2) "HomeSmoke ✓" else "HomeSmoke",
            if (stage >= 2) GREEN else OFF,
        )
        if (stage >= 3) {
            if (controllerSentOnly) setAckStage(ackController, "Передано", BLUE)
            else setAckStage(ackController, "Arduino ✓", GREEN)
        } else if (stage == 2) setAckStage(ackController, "Arduino …", ORANGE)
        else setAckStage(ackController, "Arduino", OFF)
    }

    private fun setAckError(state: String?) {
        if (ackFlow == null) return
        ackFlow!!.setVisibility(View.VISIBLE)
        setAckStage(ackRemote, "Remote ✓", GREEN)
        if ("controller_ack_timeout" == state) {
            setAckStage(ackHome, "HomeSmoke ✓", GREEN)
            setAckStage(ackController, "Arduino ✕", RED)
        } else {
            setAckStage(ackHome, "HomeSmoke ✕", RED)
            setAckStage(ackController, "Arduino", OFF)
        }
    }

    private fun setAckStage(view: TextView?, text: String?, color: Int) {
        view!!.setText(text)
        view!!.setBackground(round(color, 10))
        view!!.setTextColor(Color.WHITE)
    }

    private fun updateModeUi(raw: String?) {
        lastModeRaw = raw
        val name: String? = modeName(raw)
        val fresh: Boolean = isTelemetryFresh
        if (!fresh) {
            mode!!.setText("Последний: " + name!!)
            mode!!.setTextColor(OFF)
            mode!!.setBackgroundColor(Color.TRANSPARENT)
            mode!!.setPadding(dp(6), dp(2), dp(6), dp(2))
            return
        }
        if ("—" == name) {
            mode!!.setText("—")
            mode!!.setTextColor(OFF)
            mode!!.setBackgroundColor(Color.TRANSPARENT)
            mode!!.setPadding(dp(6), dp(2), dp(6), dp(2))
            return
        }
        var color: Int = OFF
        if ("0" == raw) color = ORANGE
        else if ("1" == raw) color = GREEN
        else if ("2" == raw) color = BLUE else if ("3" == raw) color = RED
        mode!!.setText("● " + name!!)
        mode!!.setTextColor(Color.WHITE)
        mode!!.setPadding(dp(9), dp(3), dp(9), dp(3))
        mode!!.setBackground(round(color, 12))
    }

    private fun updateHeaterUi(value: Double) {
        lastPowerValue = value
        val fresh: Boolean = isTelemetryFresh
        if (java.lang.Double.isNaN(value)) {
            power!!.setText("—")
            power!!.setTextColor(if (fresh) MUTED else OFF)
            heaterProgress!!.setProgress(0)
            if (Build.VERSION.SDK_INT >= 21)
                heaterProgress!!.setProgressTintList(ColorStateList.valueOf(OFF))
            return
        }
        val pct: Int = clamp(Math.round(value).toInt(), 0, 100)
        if (!fresh) {
            power!!.setText("Последнее: " + pct + " %")
            power!!.setTextColor(OFF)
            heaterProgress!!.setProgress(pct)
            if (Build.VERSION.SDK_INT >= 21)
                heaterProgress!!.setProgressTintList(ColorStateList.valueOf(OFF))
            return
        }
        val heating: Boolean = pct > 0
        power!!.setText(if (heating) "Нагрев · " + pct + " %" else "Выкл. · 0 %")
        power!!.setTextColor(if (heating) ORANGE else MUTED)
        heaterProgress!!.setProgress(pct)
        if (Build.VERSION.SDK_INT >= 21)
            heaterProgress!!.setProgressTintList(
                ColorStateList.valueOf(if (heating) ORANGE else OFF)
            )
    }

    private fun updateAutoUi(running: Boolean, program: String?, stage: Int, status: String?) {
        lastAutoRunning = running
        val pr: View? = autoProgram!!.getParent() as View?
        val sr: View? = autoStage!!.getParent() as View?
        pr!!.setVisibility(if (running) View.VISIBLE else View.GONE)
        sr!!.setVisibility(if (running) View.VISIBLE else View.GONE)
        if (running) {
            autoChip!!.setText("АКТИВНО")
            autoChip!!.setBackground(round(if (isTelemetryFresh) BLUE else OFF, 12))
            autoProgram!!.setText(program)
            autoStage!!.setText(if (stage > 0) (stage).toString() else "—")
            autoStatus!!.setText(
                if (status == null || status!!.trim({ it <= ' ' }).isEmpty()) "Auto работает"
                else status
            )
            autoStatus!!.setTextColor(if (isTelemetryFresh) BLUE_DARK else OFF)
        } else {
            autoChip!!.setText("ВЫКЛ")
            autoChip!!.setBackground(round(OFF, 12))
            autoStatus!!.setText("Программа не запущена")
            autoStatus!!.setTextColor(MUTED)
        }
    }

    private fun setCommandUi(txt: String?, state: Int) {
        commandState!!.setText(txt)
        var bg: Int = INFO_BG
        var border: Int = BORDER
        var color: Int = MUTED
        if (state == 1) {
            bg = WARN_BG
            border = ORANGE
            color = Color.rgb(151, 88, 0)
        } else if (state == 2) {
            bg = SUCCESS_BG
            border = GREEN
            color = Color.rgb(18, 111, 58)
        } else if (state == 3) {
            bg = ERROR_BG
            border = RED
            color = Color.rgb(170, 30, 30)
        }
        commandState!!.setTextColor(color)
        commandState!!.setBackground(roundStroke(bg, 10, border, 1))
    }

    private fun setBrokerUi(connected: Boolean, txt: String?) {
        var color: Int = if (connected) GREEN else (if (connecting) ORANGE else OFF)
        if (txt != null && txt!!.startsWith("MQTT ошибка")) color = RED
        mqttBadge!!.setTextColor(Color.WHITE)
        mqttBadge!!.setBackground(round(color, 14))
        mqttDot!!.setTextColor(color)
        brokerState!!.setText(
            if (connected) "Подключён"
            else (if (connecting) "Подключение…" else (if (color == RED) "Ошибка" else "Отключён"))
        )
        brokerState!!.setTextColor(if (connected) GREEN else (if (color == RED) RED else MUTED))
        brokerDetail!!.setText(if (txt == null) "" else txt)
        if (disconnectButton != null) {
            disconnectButton!!.setEnabled(connected || connecting)
            disconnectButton!!.setAlpha(if ((connected || connecting)) 1f else .45f)
        }
        refreshOverallState()
        refreshControlAvailability()
    }

    private fun setDeviceUi(online: Boolean, txt: String?) {
        val color: Int = if (online) GREEN else ORANGE
        deviceBadge!!.setTextColor(Color.WHITE)
        deviceBadge!!.setBackground(round(color, 14))
        deviceDot!!.setTextColor(color)
        deviceState!!.setText(
            if (online) "Онлайн" else (if (lastTelemetryAt > 0) "Данные устарели" else "Нет данных")
        )
        deviceState!!.setTextColor(
            if (online) GREEN else (if (lastTelemetryAt > 0) ORANGE else MUTED)
        )
        deviceDetail!!.setText(if (txt == null) "" else txt)
        applyTelemetryFreshness(online)
        refreshOverallState()
        refreshControlAvailability()
    }

    private fun refreshControlAvailability() {
        if (
            controlAvailability == null ||
                setButton == null ||
                stopButton == null ||
                setInput == null
        )
            return
        val mq: Boolean = mqtt != null && mqtt!!.isConnected
        val fresh: Boolean = isTelemetryFresh
        val ready: Boolean = mq && fresh
        val txt: String?
        val bg: Int
        val border: Int
        val color: Int
        if (ready) {
            txt = "Управление доступно"
            bg = SUCCESS_BG
            border = GREEN
            color = Color.rgb(18, 111, 58)
        } else if (!mq) {
            txt = "Управление недоступно · MQTT не подключён"
            bg = INFO_BG
            border = BORDER
            color = MUTED
        } else if (lastTelemetryAt <= 0) {
            txt = "Управление недоступно · нет данных от коптильни"
            bg = WARN_BG
            border = ORANGE
            color = Color.rgb(151, 88, 0)
        } else {
            txt = "Управление недоступно · данные коптильни устарели"
            bg = WARN_BG
            border = ORANGE
            color = Color.rgb(151, 88, 0)
        }
        controlAvailability!!.setText(txt)
        controlAvailability!!.setTextColor(color)
        controlAvailability!!.setBackground(roundStroke(bg, 10, border, 1))
        setButton!!.setEnabled(ready)
        stopButton!!.setEnabled(ready)
        setInput!!.setEnabled(ready)
        setButton!!.setAlpha(if (ready) 1f else .45f)
        stopButton!!.setAlpha(if (ready) 1f else .45f)
        setInput!!.setAlpha(if (ready) 1f else .65f)
    }

    private fun refreshOverallState() {
        if (systemState == null) return
        val mq: Boolean = mqtt != null && mqtt!!.isConnected
        val online: Boolean = isTelemetryFresh
        if (mq && online) {
            systemState!!.setText("ГОТОВО")
            systemState!!.setBackground(round(GREEN, 12))
        } else if (mq && lastTelemetryAt > 0) {
            systemState!!.setText("СТАРЫЕ ДАННЫЕ")
            systemState!!.setBackground(round(ORANGE, 12))
        } else if (mq) {
            systemState!!.setText("НЕТ ДАННЫХ")
            systemState!!.setBackground(round(ORANGE, 12))
        } else {
            systemState!!.setText("ОФЛАЙН")
            systemState!!.setBackground(round(OFF, 12))
        }
    }

    private fun applyTelemetryFreshness(fresh: Boolean) {
        val main: Int = if (fresh) TEXT else OFF
        val secondary: Int = if (fresh) BLUE_DARK else OFF
        val alpha: Float = if (fresh) 1f else STALE_ALPHA
        if (telemetryCamCard != null) telemetryCamCard!!.setAlpha(alpha)
        if (telemetryProbesRow != null) telemetryProbesRow!!.setAlpha(alpha)
        if (telemetryStatsRow != null) telemetryStatsRow!!.setAlpha(alpha)
        if (telemetryAutoCard != null) telemetryAutoCard!!.setAlpha(alpha)
        camera!!.setTextColor(main)
        k!!.setTextColor(main)
        t!!.setTextColor(main)
        cameraSummary!!.setTextColor(secondary)
        tempTrend!!.setTextColor(if (fresh) lastTrendColor else OFF)
        lastCommand!!.setTextColor(main)
        autoProgram!!.setTextColor(main)
        autoStage!!.setTextColor(main)
        if (heaterProgress != null) heaterProgress!!.setAlpha(if (fresh) 1f else .72f)
        if (fresh) {
            updateHeaterUi(lastPowerValue)
            updateModeUi(lastModeRaw)
            if (lastAutoRunning) {
                autoChip!!.setBackground(round(BLUE, 12))
                autoStatus!!.setTextColor(BLUE_DARK)
            } else {
                autoChip!!.setBackground(round(OFF, 12))
                autoStatus!!.setTextColor(MUTED)
            }
        } else {
            if (java.lang.Double.isNaN(lastPowerValue)) power!!.setText("Последнее: —")
            else
                power!!.setText(
                    "Последнее: " + clamp(Math.round(lastPowerValue).toInt(), 0, 100) + " %"
                )
            power!!.setTextColor(OFF)
            if (heaterProgress != null && Build.VERSION.SDK_INT >= 21)
                heaterProgress!!.setProgressTintList(ColorStateList.valueOf(OFF))
            mode!!.setText("Последний: " + modeName(lastModeRaw)!!)
            mode!!.setTextColor(OFF)
            mode!!.setBackgroundColor(Color.TRANSPARENT)
            mode!!.setPadding(dp(6), dp(2), dp(6), dp(2))
            autoChip!!.setBackground(round(OFF, 12))
            autoStatus!!.setTextColor(OFF)
        }
    }

    private fun updateCameraSummaryAndTrend() {
        val fresh: Boolean = isTelemetryFresh
        if (lastTelemetryAt <= 0) {
            cameraSummary!!.setText("Уставка — °C")
            tempTrend!!.setText("Нет данных от коптильни")
            return
        }
        if (!fresh) {
            cameraSummary!!.setText(
                if (java.lang.Double.isNaN(lastSetpointValue)) "Уставка — °C · данные устарели"
                else "Уставка " + oneDecimal(lastSetpointValue) + " °C · данные устарели"
            )
            tempTrend!!.setText("Состояние недоступно · данные устарели")
            return
        }

        if (java.lang.Double.isNaN(lastSetpointValue)) {
            cameraSummary!!.setText("Уставка — °C · Δ —")
        } else if (java.lang.Double.isNaN(lastCameraValue)) {
            cameraSummary!!.setText("Уставка " + oneDecimal(lastSetpointValue) + " °C · Δ —")
        } else {
            val d: Double = lastSetpointValue - lastCameraValue
            val delta: String?
            if (Math.abs(d) < 0.05) delta = "Δ 0,0 °C"
            else if (d > 0) delta = "Δ +" + oneDecimal(d) + " °C"
            else delta = "Δ −" + oneDecimal(-d) + " °C"
            cameraSummary!!.setText("Уставка " + oneDecimal(lastSetpointValue) + " °C · " + delta)
        }

        if (tempSamples.size < 2) {
            lastTrendColor = MUTED
            tempTrend!!.setTextColor(lastTrendColor)
            tempTrend!!.setText("Состояние камеры · анализируется")
            return
        }
        val newest: TempSample? = tempSamples.get(tempSamples.size - 1)
        var base: TempSample? = null
        for (i: Int in tempSamples.size - 2 downTo 0) {
            val x: TempSample? = tempSamples.get(i)
            if (newest!!.ts - x!!.ts >= 60000L) {
                base = x
                if (newest!!.ts - x!!.ts >= TREND_WINDOW_MS) break
            }
        }
        if (base == null) {
            lastTrendColor = MUTED
            tempTrend!!.setTextColor(lastTrendColor)
            tempTrend!!.setText("Состояние камеры · анализируется")
            return
        }
        val diff: Double = newest!!.value - base!!.value
        val minutes: Long = Math.max(1, Math.round((newest!!.ts - base!!.ts) / 60000.0))
        if (Math.abs(diff) < 0.15) {
            lastTrendColor = GREEN
            tempTrend!!.setText("Стабильно · → " + minutes + " мин")
        } else if (diff > 0) {
            lastTrendColor = ORANGE
            tempTrend!!.setText("Нагрев · ↗ +" + oneDecimal(diff) + " °C / " + minutes + " мин")
        } else {
            lastTrendColor = BLUE_DARK
            tempTrend!!.setText("Остывает · ↘ −" + oneDecimal(-diff) + " °C / " + minutes + " мин")
        }
        tempTrend!!.setTextColor(lastTrendColor)
    }

    private fun addTempSample(value: Double, ts: Long) {
        if (java.lang.Double.isNaN(value) || ts <= 0) return
        if (
            !tempSamples.isEmpty() &&
                Math.abs(tempSamples.get(tempSamples.size - 1)!!.ts - ts) < 1000L
        )
            return
        tempSamples.add(TempSample(ts, value))
        val cutoff: Long = ts - TREND_WINDOW_MS - 60000L
        while (!tempSamples.isEmpty() && tempSamples.get(0)!!.ts < cutoff) tempSamples.removeAt(0)
        while (tempSamples.size > 180) tempSamples.removeAt(0)
    }

    private fun updateLastDataCaption() {
        if (lastUpdate == null) return
        if (lastTelemetryAt <= 0) {
            lastUpdate!!.setText("Данных ещё нет")
            return
        }
        if (isTelemetryFresh) {
            val time: String? =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastTelemetryAt))
            lastUpdate!!.setText("Обновлено сейчас · " + time!!)
        } else {
            val time: String? =
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(lastTelemetryAt))
            lastUpdate!!.setText("Последние данные: " + time + " · " + relativeAge(lastTelemetryAt))
        }
    }

    private fun isFreshAt(ts: Long): Boolean {
        return ts > 0 && Math.abs(System.currentTimeMillis() - ts) <= STALE_MS
    }

    private fun normalizeTelemetryTs(ts: Long): Long {
        var ts = ts
        val now: Long = System.currentTimeMillis()
        if (ts <= 0) return now
        if (ts < 100000000000L) ts *= 1000L
        if (ts > now + 5L * 60L * 1000L) return now
        return ts
    }

    private fun relativeAge(ts: Long): String? {
        val sec: Long = Math.max(0, (System.currentTimeMillis() - ts) / 1000L)
        if (sec < 10) return "сейчас"
        if (sec < 60) return sec.toString() + " сек назад"
        val min: Long = sec / 60
        if (min < 60) return min.toString() + " мин назад"
        val hours: Long = min / 60
        val mins: Long = min % 60
        if (hours < 24)
            return hours.toString() + " ч" + (if (mins > 0) " " + mins + " мин" else "") + " назад"
        val days: Long = hours / 24
        val rem: Long = hours % 24
        return days.toString() + " д" + (if (rem > 0) " " + rem + " ч" else "") + " назад"
    }

    private fun loadCommandHistory() {
        commandHistoryItems.clear()
        try {
            val a: JSONArray = JSONArray(prefs!!.getString("command_history", "[]"))
            var i: Int = 0
            while (i < a.length() && commandHistoryItems.size < MAX_COMMAND_HISTORY) {
                val s: String? = a.optString(i, "")
                if (!s!!.isEmpty()) commandHistoryItems.add(s)
                i++
            }
        } catch (ignored: Exception) {}

        renderCommandHistory()
    }

    private fun saveCommandHistory() {
        val a: JSONArray = JSONArray()
        for (s: String? in commandHistoryItems) a.put(s)
        prefs!!.edit()!!.putString("command_history", a.toString())!!.apply()
    }

    private fun addCommandHistory(action: String?, result: String?) {
        val stamp: String? = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        commandHistoryItems.add(0, stamp + " · " + action + " · " + result)
        while (commandHistoryItems.size > MAX_COMMAND_HISTORY) commandHistoryItems.removeAt(
            commandHistoryItems.size - 1
        )
        saveCommandHistory()
        renderCommandHistory()
    }

    private fun renderCommandHistory() {
        if (commandHistory == null) return
        if (commandHistoryItems.isEmpty()) {
            commandHistory!!.setText("Команд Remote ещё не было")
            commandHistory!!.setTextColor(MUTED)
            if (commandHistoryCard != null)
                commandHistoryCard!!.setVisibility(
                    if (showTechnicalEnabled) View.VISIBLE else View.GONE
                )
            return
        }
        val b: StringBuilder = StringBuilder()
        for (i: Int in commandHistoryItems.indices) {
            if (i > 0) b.append('\n')
            b.append(commandHistoryItems.get(i))
        }
        commandHistory!!.setText(b.toString())
        commandHistory!!.setTextColor(TEXT)
        if (commandHistoryCard != null) commandHistoryCard!!.setVisibility(View.VISIBLE)
    }

    private fun disconnectInternal(ui: Boolean) {
        val c: MqttClient? = mqtt
        mqtt = null
        connecting = false
        if (c != null) c!!.close()
        if (ui) setBrokerUi(false, "MQTT отключён")
    }

    private fun showMonitor() {
        setPasswordVisible(false)
        graphVisible = false
        setPage(monitorPage)
        title!!.setText("HomeSmoke Remote")
        subtitle!!.setText("Удалённое управление")
        back!!.setVisibility(View.GONE)
        graphButton!!.setVisibility(View.VISIBLE)
        settings!!.setVisibility(View.VISIBLE)
        applyUiPreferences()
        applyTelemetryFreshness(isTelemetryFresh)
    }

    private fun showGraph() {
        setPasswordVisible(false)
        graphVisible = true
        setPage(graphPage)
        title!!.setText("График температуры")
        subtitle!!.setText("Локальная история · до 24 ч")
        back!!.setVisibility(View.VISIBLE)
        graphButton!!.setVisibility(View.GONE)
        settings!!.setVisibility(View.VISIBLE)
        refreshGraph()
    }

    private fun showSettings() {
        setPasswordVisible(false)
        graphVisible = false
        setPage(settingsPage)
        title!!.setText("Настройки MQTT")
        subtitle!!.setText("HomeSmoke Remote 2.0.13")
        back!!.setVisibility(View.VISIBLE)
        graphButton!!.setVisibility(View.GONE)
        settings!!.setVisibility(View.GONE)
        applyUiPreferences()
    }

    private fun setPage(p: View?) {
        host!!.removeAllViews()
        if (p!!.getParent() is ViewGroup) (p!!.getParent() as ViewGroup).removeView(p)
        val s: ScrollView = ScrollView(this)
        s.setFillViewport(true)
        s.setClipToPadding(false)
        s.setVerticalScrollBarEnabled(false)
        s.setHorizontalScrollBarEnabled(false)
        s.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)
        s.addView(p, android.widget.FrameLayout.LayoutParams(-1, -2))
        host!!.addView(s, LinearLayout.LayoutParams(-1, -1))
        s.post({ s.scrollTo(0, 0) })
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
    }

    private fun page(): LinearLayout? {
        val p: LinearLayout = LinearLayout(this)
        p.setOrientation(LinearLayout.VERTICAL)
        p.setPadding(dp(4), 0, dp(4), dp(16))
        p.setBackgroundColor(BG)
        return p
    }

    private fun card(): LinearLayout? {
        val c: LinearLayout = LinearLayout(this)
        c.setOrientation(LinearLayout.VERTICAL)
        c.setPadding(dp(12), dp(10), dp(12), dp(10))
        c.setBackground(roundStroke(CARD, 18, BORDER, 1))
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1).toFloat())
        return c
    }

    private fun metricCard(name: String?): LinearLayout? {
        val c: LinearLayout? = card()
        c!!.setGravity(Gravity.CENTER_HORIZONTAL)
        c!!.setPadding(dp(10), dp(8), dp(10), dp(8))
        val l: TextView? = center(name, 13, true, MUTED)
        c!!.addView(l)
        return c
    }

    private fun statusRow(
        parent: LinearLayout?,
        label: String?,
        initial: String?,
        dotColor: Int,
        mqttRow: Boolean,
    ): TextView? {
        val row: LinearLayout = LinearLayout(this)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(0, dp(5), 0, dp(1))
        val dot: TextView? = text("●", 12, true, dotColor)
        dot!!.setPadding(0, 0, dp(6), 0)
        if (mqttRow) mqttDot = dot else deviceDot = dot
        val a: TextView? = text(label, 13, true, TEXT)
        a!!.setPadding(0, 0, dp(7), 0)
        val b: TextView? = text(initial, 13, true, MUTED)
        b!!.setGravity(Gravity.END)
        b!!.setSingleLine(true)
        b!!.setEllipsize(TextUtils.TruncateAt.END)
        row.addView(dot)
        row.addView(a)
        row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        parent!!.addView(row)
        return b
    }

    private fun smallDetail(s: String?): TextView? {
        val t: TextView? = text(s, 11, false, MUTED)
        t!!.setPadding(dp(18), 0, 0, dp(1))
        t!!.setMaxLines(2)
        t!!.setEllipsize(TextUtils.TruncateAt.END)
        return t
    }

    private fun statusChip(s: String?, color: Int): TextView? {
        val t: TextView? = center(s, 10, true, Color.WHITE)
        t!!.setSingleLine(true)
        t!!.setPadding(dp(8), dp(4), dp(8), dp(4))
        t!!.setBackground(round(color, 12))
        return t
    }

    private fun info(p: LinearLayout?, label: String?, initial: String?): TextView? {
        val r: LinearLayout = LinearLayout(this)
        r.setGravity(Gravity.CENTER_VERTICAL)
        r.setPadding(0, dp(3), 0, dp(3))
        val a: TextView? = text(label, 13, false, MUTED)
        val b: TextView? = text(initial, 15, true, TEXT)
        a!!.setPadding(0, 0, dp(7), 0)
        b!!.setGravity(Gravity.END)
        b!!.setMaxLines(2)
        b!!.setEllipsize(TextUtils.TruncateAt.END)
        r.addView(a)
        r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        p!!.addView(r)
        return b
    }

    private fun sectionTitle(s: String?): TextView? {
        return text(s, 17, true, TEXT)
    }

    private fun label(s: String?): TextView? {
        return text(s, 16, true, MUTED)
    }

    private fun text(s: String?, sp: Int, bold: Boolean, color: Int): TextView? {
        val t: TextView = TextView(this)
        t.setText(s)
        t.setTextSize(sp.toFloat())
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
        return t
    }

    private fun center(s: String?, sp: Int, bold: Boolean, color: Int): TextView? {
        val t: TextView? = text(s, sp, bold, color)
        t!!.setGravity(Gravity.CENTER)
        return t
    }

    private fun badge(s: String?): TextView? {
        val t: TextView? = center(s, 10, true, Color.WHITE)
        t!!.setMinWidth(dp(42))
        t!!.setMaxLines(1)
        t!!.setPadding(dp(6), dp(6), dp(6), dp(6))
        t!!.setBackground(round(OFF, 14))
        return t
    }

    private fun iconButton(s: String?): Button? {
        val b: Button = Button(this)
        b.setText(s)
        b.setTextColor(Color.WHITE)
        b.setAllCaps(false)
        b.setPadding(0, 0, 0, 0)
        b.setBackgroundColor(Color.TRANSPARENT)
        b.setMinWidth(0)
        b.setMinimumWidth(0)
        b.setMinHeight(0)
        b.setMinimumHeight(0)
        if (Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(null)
            b.setStateListAnimator(null)
        }
        return b
    }

    private fun action(s: String?, color: Int): Button? {
        val b: Button = Button(this)
        b.setText(s)
        b.setAllCaps(false)
        b.setTextColor(Color.WHITE)
        b.setTextSize(15f)
        b.setTypeface(Typeface.DEFAULT_BOLD)
        b.setPadding(dp(7), 0, dp(7), 0)
        b.setMinWidth(0)
        b.setMinimumWidth(0)
        b.setMinHeight(0)
        b.setMinimumHeight(0)
        if (Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(null)
            b.setStateListAnimator(null)
        }
        b.setBackground(round(color, 14))
        return b
    }

    private fun check(s: String?): CheckBox? {
        val c: CheckBox = CheckBox(this)
        c.setText(s)
        c.setTextSize(14f)
        c.setTypeface(Typeface.DEFAULT_BOLD)
        c.setTextColor(TEXT)
        c.setBackgroundColor(Color.TRANSPARENT)
        c.setPadding(0, 0, 0, 0)
        c.setMinHeight(dp(44))
        if (Build.VERSION.SDK_INT >= 21) {
            val states: Array<IntArray?> =
                arrayOf<IntArray?>(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked),
                )
            c.setButtonTintList(ColorStateList(states, intArrayOf(BLUE, Color.rgb(151, 164, 180))))
            c.setStateListAnimator(null)
        }
        return c
    }

    private fun labeledField(
        parent: LinearLayout?,
        label: String?,
        hint: String?,
        type: Int,
    ): EditText? {
        val l: TextView? = text(label, 12, true, MUTED)
        l!!.setPadding(0, dp(6), 0, dp(4))
        parent!!.addView(l)
        val e: EditText? = field(hint, type)
        parent!!.addView(e, LinearLayout.LayoutParams(-1, dp(46)))
        return e
    }

    private fun field(hint: String?, type: Int): EditText? {
        val e: EditText = EditText(this)
        e.setHint(hint)
        e.setHintTextColor(Color.rgb(151, 164, 180))
        e.setInputType(type)
        e.setSingleLine(true)
        e.setTextSize(15f)
        e.setTextColor(TEXT)
        e.setPadding(dp(11), 0, dp(11), 0)
        e.setMinWidth(0)
        e.setMinimumWidth(0)
        if (Build.VERSION.SDK_INT >= 21) e.setBackgroundTintList(null)
        e.setBackground(roundStroke(Color.rgb(250, 251, 252), 13, BORDER, 1))
        return e
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

    private fun buttonMargin(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(50))
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun wrapMargin(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun checkParams(): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, dp(1), 0, 0)
        return p
    }

    private fun half(l: Int, r: Int): LinearLayout.LayoutParams? {
        val p: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        p.setMargins(dp(l), dp(4), dp(r), dp(4))
        return p
    }

    private fun dp(v: Int): Int {
        return Math.round(v * getResources()!!.getDisplayMetrics()!!.density)
    }

    private fun toast(s: String?) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT)!!.show()
    }

    private class TempSample
    internal constructor(internal val ts: Long, internal val value: Double)

    public override fun onBackPressed() {
        if (back!!.getVisibility() == View.VISIBLE) showMonitor() else super.onBackPressed()
    }

    companion object {
        private val NAVY: Int = Color.rgb(9, 47, 73)
        private val BLUE: Int = Color.rgb(31, 122, 210)
        private val BLUE_DARK: Int = Color.rgb(26, 91, 164)
        private val BG: Int = Color.rgb(245, 247, 250)
        private val CARD: Int = Color.WHITE
        private val TEXT: Int = Color.rgb(21, 31, 47)
        private val MUTED: Int = Color.rgb(101, 116, 139)
        private val BORDER: Int = Color.rgb(220, 225, 232)
        private val GREEN: Int = Color.rgb(35, 151, 83)
        private val RED: Int = Color.rgb(229, 40, 40)
        private val ORANGE: Int = Color.rgb(231, 138, 7)
        private val OFF: Int = Color.rgb(116, 129, 145)
        private val INFO_BG: Int = Color.rgb(240, 247, 255)
        private val SUCCESS_BG: Int = Color.rgb(237, 249, 242)
        private val WARN_BG: Int = Color.rgb(255, 247, 232)
        private val ERROR_BG: Int = Color.rgb(255, 239, 239)
        private val STALE_MS: Long = 10000L
        private val TREND_WINDOW_MS: Long = 5L * 60L * 1000L
        private val GRAPH_SESSION_GAP_MS: Long = 60L * 1000L
        private val MAX_COMMAND_HISTORY: Int = 5
        private val STALE_ALPHA: Float = 0.62f

        private fun graphValue(v: Double, suffix: String?): String? {
            return if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) "—"
            else oneDecimal(v)!! + suffix!!
        }

        private fun s(e: EditText?): String? {
            return e!!.getText()!!.toString()!!.trim({ it <= ' ' })
        }

        private fun topic(s: String?, d: String?): String? {
            var s = s
            s = if (s == null) "" else s!!.trim({ it <= ' ' })
            return if (s!!.isEmpty()) d else s
        }

        private fun deg(s: String?): String? {
            val v: String? = if (s == null) "" else s!!.trim({ it <= ' ' })
            return if (java.lang.Double.isNaN(parseNumber(v))) "— °C" else v!! + " °C"
        }

        private fun modeName(m: String?): String? {
            if ("0" == m) return "Ручной"
            if ("1" == m) return "PID"
            if ("2" == m) return "AUTO"
            if ("3" == m) return "STOP"
            return "—"
        }

        private fun translateState(s: String?): String? {
            if ("pid_mode_required" == s) return "сначала включите PID режим"
            if ("android_auto_running" == s) return "уставкой управляет Auto"
            if ("bluetooth_not_connected" == s) return "Bluetooth коптильни отключён"
            if ("controller_ack_timeout" == s) return "Arduino не подтвердила уставку"
            if ("stale_command" == s) return "команда устарела"
            return s
        }

        private fun safe(e: Exception?): String? {
            return if (e!!.message == null) e!!.javaClass.getSimpleName() else e!!.message
        }

        private fun parseNumber(s: String?): Double {
            try {
                return java.lang.Double.parseDouble(
                    if (s == null) "" else s!!.trim({ it <= ' ' }).replace(',', '.')
                )
            } catch (e: Exception) {
                return java.lang.Double.NaN
            }
        }

        private fun oneDecimal(v: Double): String? {
            return String.format(Locale.getDefault(), "%.1f", v)
        }

        private fun formatPlain(v: Double): String? {
            if (Math.abs(v - Math.rint(v)) < 0.000001) return (Math.rint(v).toLong()).toString()
            return oneDecimal(v)
        }

        private fun clamp(v: Int, lo: Int, hi: Int): Int {
            return Math.max(lo, Math.min(hi, v))
        }
    }
}
