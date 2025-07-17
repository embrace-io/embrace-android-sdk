package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
class FakeOtelKotlinClock(
    private val embraceClock: Clock = FakeClock(),
) : io.embrace.opentelemetry.kotlin.Clock {

    override fun now(): Long = embraceClock.now().millisToNanos()
}
