package com.bizard.homesmokeremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises the screens and persisted data across the Java -> Kotlin boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class KotlinMigrationTest {
    @Test
    fun composeBridgePreservesEmptyCredentialsGraphPreferencesAndHistoryRoute() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("auto_connect", false).apply()
        val controller = Robolectric.buildActivity(GraphUxFixActivity::class.java).setup()
        val activity = controller.get()
        try {
            val initial = activity.modernSnapshot()
            assertEquals("", initial.broker)
            assertEquals("", initial.username)
            assertFalse(initial.controlEnabled)
            activity.modernSaveSettings(ModernSettingsValues(
                "", "1883", initial.statusTopic, initial.commandTopic, initial.ackTopic,
                "", "", false, false, false,
            ))
            assertEquals("", activity.modernSnapshot().username)
            activity.modernSetGraphSeries(false, true, false, true)
            activity.modernSetGraphRange(3_600_000, false)
            assertFalse(activity.modernSnapshot().graphCamera)
            assertFalse(activity.modernSnapshot().graphK)
            assertEquals("3600000", activity.modernSnapshot().graphRangeKey)
            activity.modernShowHistory()
            assertEquals(HistoryActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun call(activity: Activity, owner: Class<*>, name: String) {
        owner.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    @Test
    fun launcherPagesAndReflectedGraphScenarioRemainOperational() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_connect", false).apply()
        val controller = Robolectric.buildActivity(GraphUxFixActivity::class.java).setup()
        val activity = controller.get()
        try {
            call(activity, MainActivity::class.java, "showSettings")
            call(activity, MainActivity::class.java, "showGraph")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            // The graph subclasses depend on these exact private JVM member names.
            val chart =
                MainActivity::class
                    .java
                    .getDeclaredField("graphChart")
                    .apply { isAccessible = true }
                    .get(activity)
            assertNotNull(chart)
            val enhanced =
                GraphUxActivity::class
                    .java
                    .getDeclaredField("graphEnhanced")
                    .apply { isAccessible = true }
                    .getBoolean(activity)
            assertTrue("Graph enhancement must survive reflection", enhanced)
            call(activity, GraphUxActivity::class.java, "startTestScenario")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
            val running =
                GraphUxActivity::class.java.getDeclaredField("testRunning").apply {
                    isAccessible = true
                }
            assertTrue(running.getBoolean(activity))
            GraphUxActivity::class
                .java
                .getDeclaredMethod("stopTestScenario", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(activity, false)
            assertFalse(running.getBoolean(activity))
            call(activity, MainActivity::class.java, "showMonitor")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun existingHistorySchemaSessionsAndChartStillWork() {
        val context = RuntimeEnvironment.getApplication()
        val history = TelemetryHistoryStore(context)
        val start = System.currentTimeMillis() - 20_000
        try {
            // Insert using the pre-migration schema and column names.
            history.writableDatabase.execSQL(
                "INSERT INTO samples (ts,camera,setpoint,probe_k,probe_t,heater) VALUES (?,?,?,?,?,?)",
                arrayOf(start, 59.0, 60.0, 30.0, null, 45.0),
            )
            history.addFresh(
                TelemetryHistoryStore.Sample(start + 5000, 60.0, 60.0, 31.0, Double.NaN, 35.0)
            )
            history.markSessionBoundary(start + 10_000)
            history.addFresh(
                TelemetryHistoryStore.Sample(start + 10_000, 61.0, 60.0, 32.0, Double.NaN, 25.0)
            )
            val samples = history.query(start, start + 15_000, 100)!!
            assertEquals(3, samples.size)
            assertEquals(2, history.countSessions(start, start + 15_000, 600_000))
            assertTrue(samples.first()!!.probeT.isNaN())
            val chart = TemperatureChartView(context)
            chart.setData(samples)
            chart.layout(0, 0, 1080, 640)
            chart.draw(Canvas(Bitmap.createBitmap(1080, 640, Bitmap.Config.ARGB_8888)))
        } finally {
            history.close()
        }
    }

    @Test
    fun secondaryScreensOpenAndClose() {
        val history = Robolectric.buildActivity(HistoryActivity::class.java).setup()
        history.pause().stop().destroy()
        val status = Robolectric.buildActivity(SystemStatusActivity::class.java).setup()
        status.pause().stop().destroy()
        val context = RuntimeEnvironment.getApplication()
        val detail =
            Robolectric.buildActivity(
                    SessionDetailActivity::class.java,
                    Intent(context, SessionDetailActivity::class.java).putExtra("session_id", -1L),
                )
                .setup()
        detail.pause().stop().destroy()
    }
}
