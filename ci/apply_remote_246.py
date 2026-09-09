#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
overlay_path = ROOT / "remote/src/main/java/com/bizard/homesmokeremote/ModernComposeOverlay.kt"
test_path = ROOT / "remote/src/test/java/com/bizard/homesmokeremote/ComposeLayoutTest.kt"

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

overlay = overlay_path.read_text(encoding="utf-8")

old_setpoint = '''                    Row(
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
'''
new_setpoint = '''                    ResponsiveSetpointControl(
                        value = setpoint,
                        enabled = s.controlEnabled,
                        onValueChange = { setpoint = it },
                        onApply = { activity.modernApplySetpoint(setpoint) },
                    )
'''
overlay = replace_once(overlay, old_setpoint, new_setpoint, "monitor setpoint")

old_test_actions = '''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
'''
new_test_actions = '''                    ResponsiveTestActions(
                        running = s.testRunning,
                        onStart = activity::modernStartTest,
                        onStop = activity::modernStopTest,
                    )
'''
overlay = replace_once(overlay, old_test_actions, new_test_actions, "graph test actions")

old_identity = '''                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
'''
new_identity = '''                    ResponsiveConnectionFields(
                        port = port,
                        username = username,
                        onPortChange = { port = it.filter(Char::isDigit) },
                        onUsernameChange = { username = it },
                    )
'''
overlay = replace_once(overlay, old_identity, new_identity, "settings connection fields")

helpers_marker = '''@androidx.compose.runtime.Composable
private fun ConnectionCard(activity: MainActivity, s: ModernRemoteSnapshot) {
'''
helpers = '''@androidx.compose.runtime.Composable
private fun ResponsiveSetpointControl(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val canApply = enabled && value.replace(',', '.').toDoubleOrNull()?.isFinite() == true
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || fontScale > 1.15f
        val field: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(
                value = value,
                onValueChange = { raw ->
                    onValueChange(raw.filter { c -> c.isDigit() || c == '.' || c == ',' })
                },
                modifier = modifier.heightIn(min = 56.dp),
                singleLine = true,
                enabled = enabled,
                label = { Text("Уставка °C") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        val apply: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            Button(
                onClick = onApply,
                enabled = canApply,
                modifier = modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text("Применить", maxLines = 1, softWrap = false, fontSize = 12.sp)
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(Modifier.fillMaxWidth())
                apply(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                field(Modifier.weight(1f))
                apply(Modifier)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ResponsiveTestActions(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 300.dp || fontScale > 1.15f
        val start: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            Button(
                onClick = onStart,
                enabled = !running,
                modifier = modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text("Запустить", maxLines = 1, softWrap = false, fontSize = 12.sp)
            }
        }
        val stop: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            OutlinedButton(
                onClick = onStop,
                enabled = running,
                modifier = modifier.heightIn(min = 48.dp),
            ) {
                Text("Остановить", maxLines = 1, softWrap = false, fontSize = 12.sp)
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                start(Modifier.fillMaxWidth())
                stop(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                start(Modifier.weight(1f))
                stop(Modifier.weight(1f))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ResponsiveConnectionFields(
    port: String,
    username: String,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || fontScale > 1.15f
        val portField: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(
                port,
                onPortChange,
                modifier = modifier.heightIn(min = 56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Порт") },
                singleLine = true,
            )
        }
        val userField: @androidx.compose.runtime.Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(
                username,
                onUsernameChange,
                modifier = modifier.heightIn(min = 56.dp),
                label = { Text("Пользователь") },
                singleLine = true,
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                portField(Modifier.fillMaxWidth())
                userField(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                portField(Modifier.weight(1f))
                userField(Modifier.weight(2f))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConnectionCard(activity: MainActivity, s: ModernRemoteSnapshot) {
'''
overlay = replace_once(overlay, helpers_marker, helpers, "responsive helpers")
overlay_path.write_text(overlay, encoding="utf-8")

tests = test_path.read_text(encoding="utf-8")
settings_anchor = '''        compose.onNodeWithText("MQTT-брокер").assertIsDisplayed()
        compose.onNodeWithText("1883").assertIsDisplayed()
        compose.onNodeWithText("Показать").assertIsDisplayed()
'''
settings_replacement = '''        compose.onNodeWithText("MQTT-брокер").assertIsDisplayed()
        compose.onNodeWithText("1883").assertIsDisplayed()
        compose.onNodeWithText("Порт").assertIsDisplayed()
        compose.onNodeWithText("Пользователь").assertIsDisplayed()
        compose.onNodeWithText("Показать").assertIsDisplayed()
'''
tests = replace_once(tests, settings_anchor, settings_replacement, "settings narrow assertions")

class_end = '''    }
}
'''
new_test = '''    }

    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowMonitorAndGraphActionsRemainUsable() {
        start(1.3f)
        compose.onNodeWithText("Уставка °C").assertIsDisplayed()
        compose.onNodeWithText("Применить").assertIsDisplayed()
        val applyLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Применить", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(applyLayout) }
        assertTrue(applyLayout.isNotEmpty())
        assertEquals("Setpoint action label must stay on one line", 1, applyLayout.single().lineCount)
        snapshot("monitor-320-font130")

        nextPage("График")
        compose.onNodeWithText("Тестовые сценарии · открыть").performScrollTo().performClick()
        compose.onNodeWithText("Запустить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Остановить").performScrollTo().assertIsDisplayed()
        val startLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Запустить", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(startLayout) }
        assertTrue(startLayout.isNotEmpty())
        assertEquals("Test action label must stay on one line", 1, startLayout.single().lineCount)
        snapshot("graph-actions-320-font130")
    }
}
'''
if tests.count(class_end) < 1:
    raise SystemExit("test class closing marker not found")
head, sep, tail = tests.rpartition(class_end)
if not sep or tail:
    raise SystemExit("unexpected test class ending")
tests = head + new_test

test_path.write_text(tests, encoding="utf-8")

# This patcher is only a transition vehicle: CI commits the resulting Kotlin/tests,
# then stages this deletion so the branch does not retain a build-time source mutator.
Path(__file__).unlink()
print("Remote 2.4.6 responsive UI patch applied")
