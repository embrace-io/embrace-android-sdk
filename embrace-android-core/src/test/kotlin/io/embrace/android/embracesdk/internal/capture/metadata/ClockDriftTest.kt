package io.embrace.android.embracesdk.internal.capture.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ClockDriftTest {

    @Test
    fun `calculateDrift returns a positive value when the aux clock is behind the wall clock`() {
        assertEquals(100L, ClockDrift.calculateDrift(wallTimeMillis = 1_000L, auxTimeMillis = 900L))
    }

    @Test
    fun `calculateDrift returns a negative value when the aux clock is ahead of the wall clock`() {
        assertEquals(-250L, ClockDrift.calculateDrift(wallTimeMillis = 1_000L, auxTimeMillis = 1_250L))
    }

    @Test
    fun `calculateDrift returns zero when the aux clock matches the wall clock`() {
        assertEquals(0L, ClockDrift.calculateDrift(wallTimeMillis = 1_000L, auxTimeMillis = 1_000L))
    }
}
