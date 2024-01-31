package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock

internal class FakeInitModule(
    clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    openTelemetryClock: io.opentelemetry.sdk.common.Clock = FakeOpenTelemetryClock(clock),
    initModule: InitModule = InitModuleImpl(clock = clock, openTelemetryClock = openTelemetryClock)
) : InitModule by initModule
