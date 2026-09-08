package com.bizard.homesmokeremote

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

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
    val lastUpdate: String,
    val controlAvailability: String,
    val graphSummary: String,
    val graphPoint: String,
    val testRunning: Boolean,
    val testScenario: String,
    val broker: String,
    val port: String,
    val statusTopic: String,
    val commandTopic: String,
    val ackTopic: String,
    val username: String,
    val tls: Boolean,
    val autoConnect: Boolean,
    val keepScreenOn: Boolean,
)

internal data class ModernSettingsValues(
    val broker: String,
    val port: String,
    val statusTopic: String,
    val commandTopic: String,
    val ackTopic: String,
    val username: String,
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
private fun ModernRemoteApp(activity: MainActivity) {
    var snapshot by remember { mutableStateOf(activity.modernSnapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = activity.modernSnapshot()
            delay(500)
        }
    }
    MaterialTheme(
        colorScheme =
            androidx.compose.material3.lightColorScheme(
                primary = Blue,
                secondary = Navy,
                background = Canvas,
                surface = Card,
                onSurface = Ink,
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Canvas),
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
            when (snapshot.page) {
                ModernRemotePage.MONITOR -> MonitorPage(activity, snapshot, padding)
                ModernRemotePage.GRAPH -> GraphPage(activity, snapshot, padding)
                ModernRemotePage.SETTINGS -> SettingsPage(activity, snapshot, padding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun ModernTopBar(activity: MainActivity, snapshot: ModernRemoteSnapshot) {
    TopAppBar(
        title = {
            Column {
                Text("HomeSmoke Remote", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    when (snapshot.page) {
                        ModernRemotePage.MONITOR -> "Удалённое управление"
                        ModernRemotePage.GRAPH -> "История температуры"
                        ModernRemotePage.SETTINGS -> "Подключение и параметры"
                    },
                    color = Color(0xFFB7C8D8),
                    fontSize = 11.sp,
                )
            }
        },
        navigationIcon = {
            if (snapshot.page != ModernRemotePage.MONITOR) {
                IconButton(onClick = activity::modernShowMonitor) {
                    Text("‹", color = Color.White, fontSize = 32.sp)
                }
            }
        },
        actions = {
            StatusPill(
                if (snapshot.mqttConnected) "MQTT онлайн" else "MQTT офлайн",
                if (snapshot.mqttConnected) Green else Color(0xFF78879A),
            )
            Spacer(Modifier.width(8.dp))
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
    NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
        NavigationBarItem(
            page == ModernRemotePage.MONITOR,
            onMonitor,
            icon = { Text("⌂") },
            label = { Text("Монитор") },
        )
        NavigationBarItem(
            page == ModernRemotePage.GRAPH,
            onGraph,
            icon = { Text("⌁") },
            label = { Text("График") },
        )
        NavigationBarItem(
            page == ModernRemotePage.SETTINGS,
            onSettings,
            icon = { Text("⚙") },
            label = { Text("Настройки") },
        )
    }
}

@androidx.compose.runtime.Composable
private fun MonitorPage(activity: MainActivity, s: ModernRemoteSnapshot, padding: PaddingValues) {
    var setpoint by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ConnectionCard(s) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Navy),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Камера", color = Color(0xFFB7C8D8), fontSize = 14.sp)
                    Text(
                        s.camera,
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.cameraSummary, color = Color(0xFF8ED0FF), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(s.trend, color = Color(0xFFB7C8D8), fontSize = 12.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Щуп K", s.probeK, Modifier.weight(1f))
                MetricCard("Щуп T", s.probeT, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("ТЭН", s.heater, Modifier.weight(1f), Amber)
                MetricCard("Режим", s.mode, Modifier.weight(1f), Blue)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Управление нагревом",
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.controlAvailability, color = Muted, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = setpoint,
                            onValueChange = {
                                setpoint = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Уставка °C") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Button(
                            onClick = { activity.modernApplySetpoint(setpoint) },
                            enabled = setpoint.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text("Применить")
                        }
                    }
                    Button(
                        onClick = activity::modernStop,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                    ) {
                        Text("STOP · выключить нагрев")
                    }
                }
            }
        }
        item { AutoCard(s) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Последняя команда", color = Muted, fontSize = 12.sp)
                    Text(
                        s.lastCommand,
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(s.commandState, color = Muted, fontSize = 12.sp)
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE4EAF1))
                    Text(s.lastUpdate, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConnectionCard(s: ModernRemoteSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Связь", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(s.brokerDetail, color = Muted, fontSize = 12.sp)
                }
                StatusPill(
                    if (s.mqttConnected) "ПОДКЛЮЧЕНО" else "ОФЛАЙН",
                    if (s.mqttConnected) Green else Red,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp)
                        .background(
                            if (s.deviceState.contains("онлайн", true)) Green else Amber,
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(s.deviceDetail, color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MetricCard(title: String, value: String, modifier: Modifier, accent: Color = Blue) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@androidx.compose.runtime.Composable
private fun AutoCard(s: ModernRemoteSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FF)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTO", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.autoProgram, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(s.autoStage, color = Muted, fontSize = 13.sp)
            Text(
                s.autoStatus,
                color = if (s.autoStatus.contains("актив", true)) Green else Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun GraphPage(activity: MainActivity, s: ModernRemoteSnapshot, padding: PaddingValues) {
    var camera by remember { mutableStateOf(true) }
    var setpoint by remember { mutableStateOf(true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Температура", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(s.graphSummary, color = Muted, fontSize = 12.sp)
                    AndroidView(
                        factory = { TemperatureChartView(it) },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        update = { view -> view.setData(activity.modernGraphSamples()) },
                    )
                    Text(s.graphPoint, color = Muted, fontSize = 12.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(camera, { camera = !camera }, label = { Text("Камера") })
                        FilterChip(setpoint, { setpoint = !setpoint }, label = { Text("Уставка") })
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E8)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Полевой тест",
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (s.testRunning) "Тест выполняется · ${s.testScenario}"
                        else "Проверьте связь и телеметрию перед запуском",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = activity::modernStartTest,
                            enabled = !s.testRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text("Запустить")
                        }
                        OutlinedButton(
                            onClick = activity::modernStopTest,
                            enabled = s.testRunning,
                        ) {
                            Text("Остановить")
                        }
                    }
                }
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
    var tls by rememberSaveable(s.tls) { mutableStateOf(s.tls) }
    var autoConnect by rememberSaveable(s.autoConnect) { mutableStateOf(s.autoConnect) }
    var keepScreenOn by rememberSaveable(s.keepScreenOn) { mutableStateOf(s.keepScreenOn) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Подключение", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        broker,
                        { broker = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("MQTT-брокер") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            port,
                            { port = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Порт") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            username,
                            { username = it },
                            modifier = Modifier.weight(2f),
                            label = { Text("Пользователь") },
                            singleLine = true,
                        )
                    }
                    SettingSwitch("TLS", tls) { tls = it }
                    SettingSwitch("Подключаться автоматически", autoConnect) { autoConnect = it }
                    SettingSwitch("Не выключать экран", keepScreenOn) { keepScreenOn = it }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Топики MQTT", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        statusTopic,
                        { statusTopic = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Телеметрия") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        commandTopic,
                        { commandTopic = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Команды") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        ackTopic,
                        { ackTopic = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Подтверждения") },
                        singleLine = true,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        activity.modernSaveSettings(
                            ModernSettingsValues(
                                broker,
                                port,
                                statusTopic,
                                commandTopic,
                                ackTopic,
                                username,
                                tls,
                                autoConnect,
                                keepScreenOn,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) {
                    Text("Сохранить")
                }
                OutlinedButton(onClick = activity::modernConnect, modifier = Modifier.weight(1f)) {
                    Text("Подключить")
                }
            }
            TextButton(onClick = activity::modernDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Отключить MQTT", color = Red)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .18f)) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
