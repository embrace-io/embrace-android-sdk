package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.Clock

/**
 * An OpenTelemetry-compatible clock used for tests that calculates the elapsed time using the difference between the current time and when
 * the instance is initialized plus the fake elapsed time that can be passed in
 */
internal class FakeOpenTelemetryClock(
    embraceClock: Clock,
    private val startingElapsedTimeNanos: Long = 0L
) : io.opentelemetry.sdk.common.Clock {

    private val realOpenTelemetryClock = OpenTelemetryClock(embraceClock = embraceClock)
    private val startingTimeNanos = now()
    override fun now(): Long = realOpenTelemetryClock.now()

    override fun nanoTime(): Long = startingElapsedTimeNanos + now() - startingTimeNanos
}
