package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
class FakeOtelKotlinClock(
    embraceClock: Clock = FakeClock(),
) : EmbClock(embraceClock = embraceClock)
