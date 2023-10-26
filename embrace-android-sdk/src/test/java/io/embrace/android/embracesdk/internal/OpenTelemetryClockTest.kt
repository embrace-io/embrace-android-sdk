package io.embrace.android.embracesdk.internal

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class OpenTelemetryClockTest {

    private lateinit var embraceClock: Clock
    private lateinit var openTelemetryClock: OpenTelemetryClock

    @Before
    fun setup() {
        embraceClock = NormalizedIntervalClock(systemClock = io.embrace.android.embracesdk.internal.clock.SystemClock())
        openTelemetryClock = OpenTelemetryClock(embraceClock = embraceClock)
    }

    @Config(sdk = [TIRAMISU])
    @Test
    fun `verify consistency in 33`() {
        verifyConsistency()
    }

    private fun verifyConsistency() {
        assertTrue(embraceClock.now() <= TimeUnit.NANOSECONDS.toMillis(openTelemetryClock.now()))
        assertTrue(openTelemetryClock.nanoTime() <= openTelemetryClock.nanoTime())
    }
}
