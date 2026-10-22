package io.embrace.android.embracesdk.internal.config.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BehaviorThresholdCheckTest {

    private lateinit var thresholdCheck: BehaviorThresholdCheck

    @Test
    fun `test new normalized DeviceId`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }
        assertEquals(0.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertEquals(100.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC0D0739" }
        assertEquals(5.08, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCED0739" }
        assertEquals(92.58, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test isBehaviourEnabled`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(0.1f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(34.9f))

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertFalse(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC0D0739" }
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(2.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.07f))
        assertTrue(thresholdCheck.isBehaviorEnabled(5.09f))
        assertTrue(thresholdCheck.isBehaviorEnabled(47.92f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
    }

    @Test
    fun `test normalized DeviceId is only calculated once`() {
        var resolutions = 0
        thresholdCheck = BehaviorThresholdCheck {
            resolutions++
            "07D85B44E4E245F4A30E559BFC0D0739"
        }
        val expected = thresholdCheck.getNormalizedDeviceId()

        repeat(5) {
            assertEquals(expected, thresholdCheck.getNormalizedDeviceId())
            assertTrue(thresholdCheck.isBehaviorEnabled(50f))
        }
        assertEquals(1, resolutions)
    }

    @Test
    fun `malformed device IDs are outside every partial rollout`() {
        listOf("", "zz", "FFFFF", "0000000ZZZZZZ", "AAAA-FFFFF").forEach { deviceId ->
            thresholdCheck = BehaviorThresholdCheck { deviceId }
            assertFalse(deviceId, thresholdCheck.isBehaviorEnabled(99.9f))
            assertTrue(deviceId, thresholdCheck.isBehaviorEnabled(100.0f))
        }
    }

    @Test
    fun `test isBehaviourEnabled with bad input`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertFalse(thresholdCheck.isBehaviorEnabled(1000f))
        assertFalse(thresholdCheck.isBehaviorEnabled(-1000f))
    }
}
