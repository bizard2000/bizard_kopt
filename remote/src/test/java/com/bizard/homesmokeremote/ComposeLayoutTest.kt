package com.bizard.homesmokeremote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the actual Compose screens, not the hidden compatibility View tree. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<GraphUxFixActivity>()
    private lateinit var renderedView: ComposeView

    private fun start(fontScale: Float = 1f) {
        compose.runOnUiThread {
            val view = ComposeView(compose.activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                        ModernRemoteApp(compose.activity)
                    }
                }
            }
            renderedView = view
            compose.activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
        compose.waitForIdle()
    }

    private fun snapshot(name: String) {
        val target = File("build/compose-screens/$name.png")
        target.parentFile!!.mkdirs()
        // Robolectric has no hardware window redraw callback for PixelCopy.
        // Draw the measured, attached ComposeView through its native Canvas instead.
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderedView.width, renderedView.height, Bitmap.Config.ARGB_8888)
            renderedView.draw(Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun nextPage(name: String) {
        compose.onNodeWithText(name).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test fun monitorGraphSettingsAndHistoryAreAccessible() {
        start()
        compose.onNodeWithText("HomeSmoke Remote").assertIsDisplayed()
        compose.onNodeWithText("Щуп K").assertIsDisplayed()
        snapshot("monitor-360")
        nextPage("График")
        compose.onNodeWithText("График температуры").assertIsDisplayed()
        compose.onNodeWithText("История пока пуста").assertIsDisplayed()
        snapshot("graph-360")
        nextPage("Настройки")
        compose.onNodeWithText("1883").assertIsDisplayed()
        compose.onNodeWithText("Показать").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNodeWithText("Тёмная").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(RemoteThemeMode.DARK, RemoteTheme.mode(compose.activity))
        snapshot("settings-360")
        compose.onNodeWithContentDescription("История и диагностика").performClick()
        compose.onNodeWithText("История и журнал").assertIsDisplayed()
        compose.onNodeWithText("Состояние системы").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowScreenAndLargerTextKeepFieldsUsable() {
        start(1.3f)
        nextPage("Настройки")
        compose.onNodeWithText("MQTT-брокер").assertIsDisplayed()
        compose.onNodeWithText("1883").assertIsDisplayed()
        compose.onNodeWithText("Порт").assertIsDisplayed()
        compose.onNodeWithText("Пользователь").assertIsDisplayed()
        compose.onNodeWithText("Показать").assertIsDisplayed()
        val settingsLabels = compose.onAllNodesWithText("Настройки", useUnmergedTree = true)
        settingsLabels.assertCountEquals(2)
        val navigationLayout = mutableListOf<TextLayoutResult>()
        settingsLabels[1].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(navigationLayout) }
        assertTrue(navigationLayout.isNotEmpty())
        assertEquals("Bottom navigation label must stay on one line", 1, navigationLayout.single().lineCount)
        snapshot("settings-320-font130")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
        compose.onNodeWithText("Подключить").assertIsDisplayed()
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Подключить", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertTrue(textLayouts.isNotEmpty())
        assertEquals("Connection label must not wrap on a narrow screen", 1, textLayouts.single().lineCount)
        snapshot("settings-actions-320-font130")
        val layout = textLayouts.single()
        // Paragraph geometry is fractional but the measured text size uses whole pixels.
        assertTrue("Label right edge ${layout.getLineRight(0)} exceeds ${layout.size.width}",
            layout.getLineRight(0) <= layout.size.width + 1f)
        assertTrue("Label bottom ${layout.getLineBottom(0)} exceeds ${layout.size.height}",
            layout.getLineBottom(0) <= layout.size.height + 1f)
    }

    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowMonitorAndGraphActionsRemainUsable() {
        start(1.3f)
        compose.onNodeWithText("Уставка °C").assertIsDisplayed()
        compose.onNodeWithText("Применить").performScrollTo().assertIsDisplayed()
        val applyLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Применить", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(applyLayout) }
        assertTrue(applyLayout.isNotEmpty())
        assertEquals("Setpoint action label must stay on one line", 1, applyLayout.single().lineCount)
        snapshot("monitor-320-font130")

        nextPage("График")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Тестовые сценарии · открыть").assertIsDisplayed().performClick()
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
