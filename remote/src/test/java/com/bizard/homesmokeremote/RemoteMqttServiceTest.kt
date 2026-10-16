package com.bizard.homesmokeremote

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The background recorder uses the existing status payload and rejects retained stale data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemoteMqttServiceTest {
    private val now = 1_790_000_000_000L
    private fun frame(ts: Long) =
        """{"ts":$ts,"temp_ds":"48,5","temp_k":50,"temp_tip_k":49.2,"temp_tip_t":53.1,"heater_power":44}"""

    @Test fun storesFreshStatusUsingExistingFields() {
        val sample = RemoteMqttService.freshSample(frame(now), now)
        assertNotNull(sample)
        assertEquals(now, sample!!.ts)
        assertEquals(48.5, sample.camera, 0.001)
        assertEquals(50.0, sample.setpoint, 0.001)
        assertEquals(49.2, sample.probeK, 0.001)
        assertEquals(53.1, sample.probeT, 0.001)
        assertEquals(44.0, sample.heater, 0.001)
    }

    @Test fun retainedOlderThanTenSecondsIsNotWritten() {
        assertNull(RemoteMqttService.freshSample(frame(now - 20_000), now))
    }

    @Test fun unixSecondsAreNormalizedLikeCurrentActivity() {
        assertEquals(now, RemoteMqttService.freshSample(frame(now / 1000), now)!!.ts)
    }
}
