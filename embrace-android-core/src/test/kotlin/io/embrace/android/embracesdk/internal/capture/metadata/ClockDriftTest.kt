package io.embrace.android.embracesdk.internal.capture.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ClockDriftTest {

    @Test
    fun `both drifts are null when network and gnss times are null`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = null,
            gnssTimeMillis = null,
        )

        assertNull(drift.networkDriftMillis)
        assertNull(drift.gnssDriftMillis)
    }

    @Test
    fun `positive drift when aux clock is behind wall clock`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = 900L,
            gnssTimeMillis = 800L,
        )

        assertEquals(100L, drift.networkDriftMillis)
        assertEquals(200L, drift.gnssDriftMillis)
    }

    @Test
    fun `negative drift when aux clock is ahead of wall clock`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = 1_100L,
            gnssTimeMillis = 1_300L,
        )

        assertEquals(-100L, drift.networkDriftMillis)
        assertEquals(-300L, drift.gnssDriftMillis)
    }

    @Test
    fun `zero drift when aux clock matches wall clock`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = 1_000L,
            gnssTimeMillis = 1_000L,
        )

        assertEquals(0L, drift.networkDriftMillis)
        assertEquals(0L, drift.gnssDriftMillis)
    }

    @Test
    fun `network drift is computed when gnss is null`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = 950L,
            gnssTimeMillis = null,
        )

        assertEquals(50L, drift.networkDriftMillis)
        assertNull(drift.gnssDriftMillis)
    }

    @Test
    fun `gnss drift is computed when network is null`() {
        val drift = ClockDrift.fromWallDrift(
            wallTimeMillis = 1_000L,
            networkTimeMillis = null,
            gnssTimeMillis = 1_050L,
        )

        assertNull(drift.networkDriftMillis)
        assertEquals(-50L, drift.gnssDriftMillis)
    }
}
