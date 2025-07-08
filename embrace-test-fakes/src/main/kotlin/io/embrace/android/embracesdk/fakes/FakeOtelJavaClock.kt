package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.otel.impl.EmbOtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock

/**
 * An OpenTelemetry-compatible clock used for tests that calculates the elapsed time using the difference between the current time and when
 * the instance is initialized plus the fake elapsed time that can be passed in
 */
class FakeOtelJavaClock(
    embraceClock: Clock,
    private val startingElapsedTimeNanos: Long = 0L,
) : OtelJavaClock {

    private val realOpenTelemetryClock = EmbOtelJavaClock(embraceClock = embraceClock)
    private val startingTimeNanos = now()
    override fun now(): Long = realOpenTelemetryClock.now()

    override fun nanoTime(): Long = startingElapsedTimeNanos + now() - startingTimeNanos
}
