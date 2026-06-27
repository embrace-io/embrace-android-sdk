package io.embrace.android.embracesdk.internal.otel.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
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
