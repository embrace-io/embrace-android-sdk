package io.embrace.android.embracesdk.internal.otel.impl

import android.os.SystemClock
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.opentelemetry.kotlin.ExperimentalApi

/**
 * A clock that is compatible with the OpenTelemetry SDK that defers to the internal clock used by Embrace. This allows the times recorded
 * internally by the OpenTelemetry SDK to use the same clock instance as the Embrace SDK, which is anchored to the start time of the app
 * so as to not be affected by any client-side clock changes or drifts.
 *
 * The one caveat about this implementation is that the precision for obtaining the current time only goes to the millisecond, which is
 * considered enough for client side operation timings at this time.
 */
@OptIn(ExperimentalApi::class)
class EmbClock(
    private val embraceClock: Clock,
) : io.embrace.opentelemetry.kotlin.Clock {

    override fun now(): Long = embraceClock.now().millisToNanos()

    fun nanoTime(): Long = SystemClock.elapsedRealtimeNanos()
}
