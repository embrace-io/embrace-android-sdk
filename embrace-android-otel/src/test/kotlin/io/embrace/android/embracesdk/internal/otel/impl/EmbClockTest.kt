package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbClockTest {

    private lateinit var embraceClock: Clock
    private lateinit var openTelemetryClock: EmbClock

    @Before
    fun setup() {
        embraceClock = NormalizedIntervalClock()
        openTelemetryClock = EmbClock(embraceClock = embraceClock)
    }

    @Test
    fun `verify consistency`() {
        assertTrue(embraceClock.now() <= TimeUnit.NANOSECONDS.toMillis(openTelemetryClock.now()))
        assertTrue(openTelemetryClock.nanoTime() <= openTelemetryClock.nanoTime())
    }
}
