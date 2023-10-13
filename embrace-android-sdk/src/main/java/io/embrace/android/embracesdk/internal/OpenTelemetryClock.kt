package io.embrace.android.embracesdk.internal

import android.os.SystemClock
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.utils.BuildVersionChecker
import java.util.concurrent.TimeUnit

/**
 * A clock that is compatible with the OpenTelemetry SDK that defers to the internal clock used by Embrace. This allows the times recorded
 * internally by the OpenTelemetry SDK to use the same clock instance as the Embrace SDK, which is anchored to the start time of the app
 * so as to not be affected by any client-side clock changes or drifts.
 *
 * The one caveat about this implementation is that the precision for obtaining the current time only goes to the millisecond, which is
 * considered enough for client side operation timings at this time.
 */
internal class OpenTelemetryClock(
    private val embraceClock: Clock
) : io.opentelemetry.sdk.common.Clock {

    override fun now(): Long = TimeUnit.MILLISECONDS.toNanos(embraceClock.now())

    override fun nanoTime(): Long {
        return if (BuildVersionChecker.isAtLeast(17)) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime())
        }
    }
}
