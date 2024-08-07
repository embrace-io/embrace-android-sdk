package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.createInitModule
import io.embrace.android.embracesdk.internal.injection.createOpenTelemetryModule
import io.embrace.android.embracesdk.internal.logging.EmbLogger

public class FakeInitModule(
    clock: Clock = FakeClock(),
    logger: EmbLogger = FakeEmbLogger(),
    systemInfo: SystemInfo = SystemInfo(
        osVersion = "99.0.0",
        deviceManufacturer = "Fake Manufacturer",
        deviceModel = "Phake Phone Phive"
    ),
    initModule: InitModule = createInitModule(
        clock = clock,
        logger = logger,
        systemInfo = systemInfo
    )
) : InitModule by initModule {
    public val openTelemetryModule: OpenTelemetryModule = createOpenTelemetryModule(initModule)

    public fun getFakeClock(): FakeClock? = clock as? FakeClock
}
