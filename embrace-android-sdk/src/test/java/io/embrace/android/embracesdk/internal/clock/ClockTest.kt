package io.embrace.android.embracesdk.internal.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class ClockTest {
    @Test
    fun `test timestamp fallback conversion`() {
        // We need to fix this before the year 5000. I will be dead by then so it'll be someone else's problem.
        val systemCurrentTimeMs = System.currentTimeMillis()
        assertEquals(systemCurrentTimeMs, systemCurrentTimeMs.normalizeTimestampAsMillis())
        assertEquals(JAN_1_2025_MS, JAN_1_2025_MS.normalizeTimestampAsMillis())
        assertEquals(BEFORE_CUTOFF, BEFORE_CUTOFF.normalizeTimestampAsMillis())
        assertNotEquals(MAX_MS_CUTOFF, MAX_MS_CUTOFF.normalizeTimestampAsMillis())
    }

    companion object {
        private const val JAN_1_2025_MS = 1_735_689_600_000L
        private const val BEFORE_CUTOFF = MAX_MS_CUTOFF - 1L
    }
}
