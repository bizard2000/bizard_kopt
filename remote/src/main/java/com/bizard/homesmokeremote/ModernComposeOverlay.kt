package com.bizard.homesmokeremote

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Typography
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class ModernRemotePage {
    MONITOR,
    GRAPH,
    SETTINGS,
}

internal data class ModernRemoteSnapshot(
    val page: ModernRemotePage,
    val mqttConnected: Boolean,
    val brokerState: String,
    val brokerDetail: String,
    val deviceState: String,
    val deviceDetail: String,
    val camera: String,
    val cameraSummary: String,
    val trend: String,
    val probeK: String,
    val probeT: String,
    val heater: String,
    val mode: String,
    val autoProgram: String,
    val autoStage: String,
    val autoStatus: String,
    val lastCommand: String,
    val commandState: String,
    val commandHistory: String,
    val ackRemote: String,
    val ackHome: String,
    val ackController: String,
    val lastUpdate: String,
    val controlAvailability: String,
    val controlEnabled: Boolean,
    val graphRangeKey: String,
    val graphCamera: Boolean,
    val graphSetpoint: Boolean,
    val graphK: Boolean,
    val graphT: Boolean,
    val graphSummary: String,
    val graphPoint: String,
    val testRunning: Boolean,
    val testScenario: String,
    val testScenarioIndex: Int,
    val broker: String,
    val port: String,
    val statusTopic: String,
    val commandTopic: String,
    val ackTopic: String,
    val username: String,
    val passwordConfigured: Boolean,
    val tls: Boolean,
    val autoConnect: Boolean,
    val keepScreenOn: Boolean,
    val technicalData: Boolean,
    val notifyConnection: Boolean,
    val notifySetpoint: Boolean,
    val notifySession: Boolean,
)

internal data class ModernSettingsValues(
    val broker: String,
    val port: String,
    val statusTopic: String,
    val commandTopic: String,
    val ackTopic: String,
    val username: String,
    val password: String,
    val tls: Boolean,
    val autoConnect: Boolean,
    val keepScreenOn: Boolean,
)

internal object ModernComposeOverlay {
    fun install(activity: MainActivity) {
        val compose =
            ComposeView(activity).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent { ModernRemoteApp(activity) }
            }
        activity.addContentView(
            compose,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}

private val Ink = Color(0xFF132238)
private val Muted = Color(0xFF66758A)
private val Canvas = Color(0xFFF4F7FB)
private val Navy = Color(0xFF082F49)
private val Blue = Color(0xFF1F7AD2)
private val Green = Color(0xFF239753)
private val Red = Color(0xFFE52828)
private val Amber = Color(0xFFE78A07)
private val Card = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
internal fun ModernRemoteApp(activity: MainActivity) {
    var snapshot by remember { mutableStateOf(activity.modernSnapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = activity.modernSnapshot()
            delay(500)
        }
    }
    val pageState = rememberSaveableStateHolder()
    MaterialTheme(
        typography = Typography(
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        ),
        colorScheme =
            androidx.compose.material3.lightColorScheme(
                primary = Blue,
                secondary = Navy,
                background = Canvas,
                surface = Card,
                onSurface = Ink,
                onSurfaceVariant = Muted,
                primaryContainer = Color(0xFFE3EFFA),
                onPrimaryContainer = Navy,
                secondaryContainer = Color(0xFFE3EFFA),
                onSecondaryContainer = Navy,
                surfaceVariant = Color(0xFFEAF0F6),
                surfaceTint = Blue,
                outline = Color(0xFF7B8898),
                error = Red,
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Canvas).imePadding(),
            topBar = { ModernTopBar(activity, snapshot) },
            bottomBar = {
                ModernNavigation(
                    snapshot.page,
                    onMonitor = activity::modernShowMonitor,
                    onGraph = activity::modernShowGraph,
                    onSettings = activity::modernShowSettings,
                )
            },
        ) { padding ->
            pageState.SaveableStateProvider(snapshot.page.name) {
                when (snapshot.page) {
                    ModernRemotePage.MONITOR -> MonitorPage(activity, snapshot, padding)
                    ModernRemotePage.GRAPH -> GraphPage(activity, snapshot, padding)
                    ModernRemotePage.SETTINGS -> SettingsPage(activity, snapshot, padding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun ModernTopBar(activity: MainActivity, snapshot: ModernRemoteSnapshot) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        // The default height includes room for content in addition to system insets.
        title = {
            Column {
                Text(
                    when (snapshot.page) {
                        ModernRemotePage.MONITOR -> "HomeSmoke Remote"
                        ModernRemotePage.GRAPH -> "График температуры"
                        ModernRemotePage.SETTINGS -> "Настройки"
                    },
                    color = Color.White, fontSize = 18.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (snapshot.testRunning) "ТЕСТОВЫЕ ДАННЫЕ"
                    else if (snapshot.page == ModernRemotePage.SETTINGS) activity.modernVersion()
                    else if (snapshot.mqttConnected) "MQTT подключён" else "MQTT отключён",
                    color = Color(0xFFD2E1EE), style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "История и диагностика", tint = Color.White)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("История и журнал") },
                        onClick = { menuExpanded = false; activity.modernShowHistory() },
                    )
                    DropdownMenuItem(
                        text = { Text("Состояние системы") },
                        onClick = { menuExpanded = false; activity.modernShowSystemStatus() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
    )
}

@androidx.compose.runtime.Composable
private fun ModernNavigation(
    page: ModernRemotePage,
    onMonitor: () -> Unit,
    onGraph: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            page == ModernRemotePage.MONITOR,
            onMonitor,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Монитор") },
        )
        NavigationBarItem(
            page == ModernRemotePage.GRAPH,
            onGraph,
            icon = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_chart), contentDescription = null) },
            label = { Text("График") },
        )
        NavigationBarItem(
            page == ModernRemotePage.SETTINGS,
            onSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Настройки") },
        )
    }
}

@androidx.compose.runtime.Composable
private fun MonitorPage(activity: MainActivity, s: ModernRemoteSnapshot, padding: PaddingValues) {
    var setpoint by rememberSaveable { mutableStateOf("") }
    var technicalExpanded by rememberSaveable(s.technicalData) { mutableStateOf(s.technicalData) }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding).consumeWindowInsets(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ConnectionCard(s) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Камера", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(s.camera, color = Navy, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                    Text(s.cameraSummary, color = Blue, style = MaterialTheme.typography.bodyMedium)
                    Text(s.trend, color = Muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = Color(0xFFE2E8EF))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("Щуп K", s.probeK, Modifier.weight(1f))
                        MetricCard("Щуп T", s.probeT, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("ТЭН", s.heater, Modifier.weight(1f), Amber)
                        MetricCard("Режим", s.mode, Modifier.weight(1f), Navy)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Управление нагревом",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.controlAvailability, color = Muted, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = setpoint,
                            onValueChange = {
                                setpoint = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            singleLine = true,
                            enabled = s.controlEnabled,
                            label = { Text("Уставка °C") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Button(
                            onClick = { activity.modernApplySetpoint(setpoint) },
                            enabled = s.controlEnabled && setpoint.replace(',', '.').toDoubleOrNull()?.isFinite() == true,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text("Применить", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = activity::modernStop,
                        enabled = s.controlEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                    ) {
                        Text("STOP · выключить нагрев", fontSize = 12.sp)
                    }
                }
            }
        }
        item { AutoCard(s) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0F6)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Диагностика и команды", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(s.commandState, color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { technicalExpanded = !technicalExpanded }) {
                            Text(if (technicalExpanded) "Скрыть" else "Подробно", fontSize = 12.sp)
                        }
                    }
                    if (technicalExpanded) {
                        AckFlow(s)
                        HorizontalDivider(Modifier.padding(vertical = 5.dp), color = Color(0xFFD7E0E9))
                        Text("Последняя команда контроллера", color = Muted, fontSize = 12.sp)
                        Text(s.lastCommand, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("История Remote", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        Text(s.commandHistory, color = Ink, fontSize = 12.sp)
                        Text(s.lastUpdate, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    } else {
                        Text("Последняя команда: ${s.lastCommand}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConnectionCard(s: ModernRemoteSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Связь", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (s.technicalData) Text(s.brokerDetail, color = Muted, fontSize = 12.sp)
                }
                StatusPill(
                    if (s.mqttConnected) "ПОДКЛЮЧЕНО" else "ОФЛАЙН",
                    if (s.mqttConnected) Green else Red,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp)
                        .background(
                            if (s.deviceState.contains("онлайн", true)) Green else Amber,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(if (s.technicalData) s.deviceDetail else s.deviceState, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MetricCard(title: String, value: String, modifier: Modifier, accent: Color = Blue) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = accent, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@androidx.compose.runtime.Composable
private fun AutoCard(s: ModernRemoteSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FF)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTO", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.autoProgram, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (s.autoStage != "—") Text(s.autoStage, color = Muted, fontSize = 12.sp)
            Text(
                s.autoStatus,
                color = if (s.autoStatus.contains("актив", true)) Green else Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AckFlow(s: ModernRemoteSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.ackRemote, color = Ink, style = MaterialTheme.typography.bodyMedium)
        Text(s.ackHome, color = Ink, style = MaterialTheme.typography.bodyMedium)
        Text(s.ackController, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@androidx.compose.runtime.Composable
private fun GraphPage(activity: MainActivity, s: ModernRemoteSnapshot, padding: PaddingValues) {
    var camera by rememberSaveable(s.graphCamera) { mutableStateOf(s.graphCamera) }
    var setpoint by rememberSaveable(s.graphSetpoint) { mutableStateOf(s.graphSetpoint) }
    var probeK by rememberSaveable(s.graphK) { mutableStateOf(s.graphK) }
    var probeT by rememberSaveable(s.graphT) { mutableStateOf(s.graphT) }
    var selectedPoint by remember { mutableStateOf("Коснитесь графика, чтобы увидеть точные значения.") }
    var scenarioIndex by rememberSaveable(s.testScenarioIndex) { mutableStateOf(s.testScenarioIndex) }
    var scenarioMenuExpanded by remember { mutableStateOf(false) }
    val scenarios = listOf("Полный цикл", "Нагрев камеры", "Стабилизация PID", "Auto-программа", "Щуп достигает цели", "Потеря связи")
    var rangeKey by rememberSaveable(s.graphRangeKey) { mutableStateOf(s.graphRangeKey) }
    var testExpanded by rememberSaveable { mutableStateOf(false) }
    val samples = activity.modernGraphSamples()
    LaunchedEffect(camera, setpoint, probeK, probeT) {
        activity.modernSetGraphSeries(camera, setpoint, probeK, probeT)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding).consumeWindowInsets(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Температура", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(s.graphSummary, color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val ranges = listOf(
                            "1ч" to (1L * 60L * 60L * 1000L to false),
                            "3ч" to (3L * 60L * 60L * 1000L to false),
                            "6ч" to (6L * 60L * 60L * 1000L to false),
                            "12ч" to (12L * 60L * 60L * 1000L to false),
                            "24ч" to (24L * 60L * 60L * 1000L to false),
                            "Сеанс" to (0L to true),
                        )
                        ranges.forEach { (label, value) ->
                            FilterChip(
                                selected = rangeKey == if (value.second) "session" else value.first.toString(),
                                onClick = {
                                    rangeKey = if (value.second) "session" else value.first.toString()
                                    activity.modernSetGraphRange(value.first, value.second)
                                    selectedPoint = "Коснитесь графика, чтобы увидеть точные значения."
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(label, fontSize = 12.sp) },
                            )
                        }
                    }
                    if (samples.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("История пока пуста", color = Ink, fontWeight = FontWeight.Medium)
                            Text("График появится после получения свежей телеметрии.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    } else AndroidView(
                        factory = {
                            TemperatureChartView(it).apply {
                                setSeries(camera, setpoint, probeK, probeT)
                                setOnSelectionListener { sample ->
                                    selectedPoint = sample?.let(::modernPointText)
                                        ?: "Коснитесь графика, чтобы увидеть точные значения."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        update = { view ->
                            view.setSeries(camera, setpoint, probeK, probeT)
                            view.setData(samples)
                        },
                    )
                    if (samples.isNotEmpty()) Text(selectedPoint.ifBlank { s.graphPoint }, color = Muted, fontSize = 12.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        FilterChip(camera, { camera = !camera }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Камера", fontSize = 12.sp) })
                        FilterChip(setpoint, { setpoint = !setpoint }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Уставка", fontSize = 12.sp) })
                        FilterChip(probeK, { probeK = !probeK }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Щуп K", fontSize = 12.sp) })
                        FilterChip(probeT, { probeT = !probeT }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Щуп T", fontSize = 12.sp) })
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E8)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(onClick = { testExpanded = !testExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (testExpanded || s.testRunning) "Тестовые сценарии · скрыть" else "Тестовые сценарии · открыть")
                    }
                    if (testExpanded || s.testRunning) {
                    Text(
                        if (s.testRunning) "Тест выполняется · ${s.testScenario}"
                        else "Локальная симуляция: MQTT отключается, данные тестовые. Это не проверка связи с коптильней.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { scenarioMenuExpanded = true },
                            enabled = !s.testRunning,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(scenarios.getOrElse(scenarioIndex) { s.testScenario }, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = scenarioMenuExpanded,
                            onDismissRequest = { scenarioMenuExpanded = false },
                        ) {
                            scenarios.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name, fontSize = 12.sp) },
                                    onClick = {
                                        scenarioIndex = index
                                        scenarioMenuExpanded = false
                                        activity.modernSetTestScenario(index)
                                    },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = activity::modernStartTest,
                            enabled = !s.testRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text("Запустить", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = activity::modernStopTest,
                            enabled = s.testRunning,
                        ) {
                            Text("Остановить", fontSize = 12.sp)
                        }
                    }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = activity::modernShowHistory, modifier = Modifier.fillMaxWidth()) {
                Text("История сеансов и журнал")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsPage(activity: MainActivity, s: ModernRemoteSnapshot, padding: PaddingValues) {
    var broker by rememberSaveable(s.broker) { mutableStateOf(s.broker) }
    var port by rememberSaveable(s.port) { mutableStateOf(s.port) }
    var statusTopic by rememberSaveable(s.statusTopic) { mutableStateOf(s.statusTopic) }
    var commandTopic by rememberSaveable(s.commandTopic) { mutableStateOf(s.commandTopic) }
    var ackTopic by rememberSaveable(s.ackTopic) { mutableStateOf(s.ackTopic) }
    var username by rememberSaveable(s.username) { mutableStateOf(s.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var topicsExpanded by rememberSaveable { mutableStateOf(false) }
    var tls by rememberSaveable(s.tls) { mutableStateOf(s.tls) }
    var autoConnect by rememberSaveable(s.autoConnect) { mutableStateOf(s.autoConnect) }
    var keepScreenOn by rememberSaveable(s.keepScreenOn) { mutableStateOf(s.keepScreenOn) }
    var technicalData by rememberSaveable(s.technicalData) { mutableStateOf(s.technicalData) }
    var notifyConnection by rememberSaveable(s.notifyConnection) { mutableStateOf(s.notifyConnection) }
    var notifySetpoint by rememberSaveable(s.notifySetpoint) { mutableStateOf(s.notifySetpoint) }
    var notifySession by rememberSaveable(s.notifySession) { mutableStateOf(s.notifySession) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding).consumeWindowInsets(padding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Подключение", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        broker,
                        { broker = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("MQTT-брокер") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            port,
                            { port = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Порт") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            username,
                            { username = it },
                            modifier = Modifier.weight(2f).heightIn(min = 56.dp),
                            label = { Text("Пользователь") },
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("Пароль") },
                        supportingText = {
                            if (s.passwordConfigured && password.isBlank()) Text("Пароль сохранён; оставьте поле пустым, чтобы сохранить его")
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Скрыть" else "Показать", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        singleLine = true,
                    )
                    SettingSwitch("TLS", tls) { tls = it }
                    SettingSwitch("Подключаться автоматически", autoConnect) { autoConnect = it }
                    SettingSwitch("Не выключать экран", keepScreenOn) { keepScreenOn = it }
                    SettingSwitch("Показывать технические данные", technicalData) {
                        technicalData = it
                        activity.modernSetTechnical(it)
                    }
                }
            }
        }
        item {
            SettingsActions(
                onSave = {
                    activity.modernSaveSettings(ModernSettingsValues(
                        broker, port, statusTopic, commandTopic, ackTopic,
                        username, password, tls, autoConnect, keepScreenOn,
                    ))
                },
                onConnect = {
                    activity.modernSaveSettings(ModernSettingsValues(
                        broker, port, statusTopic, commandTopic, ackTopic,
                        username, password, tls, autoConnect, keepScreenOn,
                    ), connect = true)
                },
                onDisconnect = activity::modernDisconnect,
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Уведомления", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    SettingSwitch("Потеря и восстановление связи", notifyConnection) {
                        notifyConnection = it
                        activity.modernSetNotification("notify_connection", it)
                    }
                    SettingSwitch("Камера достигла уставки", notifySetpoint) {
                        notifySetpoint = it
                        activity.modernSetNotification("notify_setpoint", it)
                    }
                    SettingSwitch("Начало и завершение сеанса", notifySession) {
                        notifySession = it
                        activity.modernSetNotification("notify_session", it)
                    }
                    Text(
                        "Уведомления формируются локально по телеметрии; для Android 13+ требуется системное разрешение.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { topicsExpanded = !topicsExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (topicsExpanded) "Топики MQTT · скрыть" else "Топики MQTT · настроить")
                    }
                    if (topicsExpanded) {
                    OutlinedTextField(
                        statusTopic,
                        { statusTopic = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("Телеметрия") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        commandTopic,
                        { commandTopic = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("Команды") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        ackTopic,
                        { ackTopic = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("Подтверждения") },
                        singleLine = true,
                    )
                    }
                }
            }
        }

    }
}

@androidx.compose.runtime.Composable
private fun SettingsActions(onSave: () -> Unit, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.15f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
                OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Подключить") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Подключить") }
            }
        }
    }
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("Отключить MQTT", color = Red)
    }
}

@androidx.compose.runtime.Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .18f)) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

private fun modernPointText(sample: TelemetryHistoryStore.Sample): String {
    val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(sample.ts))
    return buildString {
        append(time)
        append("\nКамера ")
        append(modernValue(sample.camera, " °C"))
        append(" · Уставка ")
        append(modernValue(sample.setpoint, " °C"))
        append("\nЩуп K ")
        append(modernValue(sample.probeK, " °C"))
        append(" · Щуп T ")
        append(modernValue(sample.probeT, " °C"))
        append(" · ТЭН ")
        append(modernValue(sample.heater, " %"))
    }
}

private fun modernValue(value: Double, suffix: String): String {
    return if (value.isNaN()) "—" else String.format(Locale.getDefault(), "%.1f%s", value, suffix)
}
