package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.logging.EmbLoggerImpl

internal class FakeInitModule(
    clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    openTelemetryClock: io.opentelemetry.sdk.common.Clock = FakeOpenTelemetryClock(clock),
    logger: EmbLoggerImpl = EmbLoggerImpl(),
    systemInfo: SystemInfo = SystemInfo(
        osVersion = "99.0.0",
        deviceManufacturer = "Fake Manufacturer",
        deviceModel = "Phake Phone Phive"
    ),
    initModule: InitModule = InitModuleImpl(
        clock = clock,
        openTelemetryClock = openTelemetryClock,
        logger = logger,
        systemInfo = systemInfo
    )
) : InitModule by initModule {
    val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(initModule)

    fun getFakeClock(): FakeClock? = clock as? FakeClock
}
