package io.embrace.android.embracesdk.internal.clock

import org.junit.Assert.assertEquals
import org.junit.Test

internal class NormalizedIntervalClockTest {

    private var elapsedMs = 10_000L
    private val elapsedProvider = { elapsedMs }

    @Test
    fun `time is normalized to the wall clock at construction`() {
        val clock = buildClock()
        assertEquals(1_000_000L, clock.now())
        elapsedMs += 1_000L
        assertEquals(1_001_000L, clock.now())
    }

    @Test
    fun `backwards reading is returned as is`() {
        val clock = buildClock()
        elapsedMs += 70_000L
        assertEquals(1_070_000L, clock.now())
        elapsedMs -= 60_001L
        assertEquals(1_009_999L, clock.now())
    }

    @Test
    fun `cas exhaustion returns current time`() {
        val clock = buildClock(casLimit = 0)
        assertEquals(1_000_000L, clock.now())
        elapsedMs += 1_000L
        assertEquals(1_001_000L, clock.now())
    }

    private fun buildClock(casLimit: Int = 8) = NormalizedIntervalClock(
        wallClock = { 1_000_000L },
        monotonicClock = elapsedProvider,
        casLimit = casLimit,
    )
}
