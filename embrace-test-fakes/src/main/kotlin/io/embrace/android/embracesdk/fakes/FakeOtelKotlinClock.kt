package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos

class FakeOtelKotlinClock(
    private val embraceClock: Clock,
) : io.embrace.opentelemetry.kotlin.Clock {

    override fun now(): Long = embraceClock.now().millisToNanos()
}
