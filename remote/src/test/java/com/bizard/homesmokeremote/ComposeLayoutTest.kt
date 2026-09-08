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
        compose.onNodeWithText("Показать").assertIsDisplayed()
        snapshot("settings-320-font130")
        compose.onNodeWithText("Сохранить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подключить").performScrollTo().assertIsDisplayed()
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Подключить", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertTrue(textLayouts.isNotEmpty())
        assertEquals("Connection label must not wrap on a narrow screen", 1, textLayouts.single().lineCount)
        assertFalse(textLayouts.single().hasVisualOverflow)
        snapshot("settings-actions-320-font130")
    }
}
