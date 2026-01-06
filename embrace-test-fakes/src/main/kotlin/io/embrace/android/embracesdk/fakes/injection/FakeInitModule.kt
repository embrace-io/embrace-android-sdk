package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger

class FakeInitModule(
    clock: Clock = FakeClock(),
    logger: EmbLogger = FakeEmbLogger(),
    systemInfo: SystemInfo = SystemInfo(
        osVersion = "99.0.0",
        deviceManufacturer = "Fake Manufacturer",
        deviceModel = "Phake Phone Phive"
    ),
    initModule: InitModule = InitModuleImpl(
        logger = logger,
        clock = clock,
        systemInfo = systemInfo
    ),
    override var instrumentedConfig: InstrumentedConfig = FakeInstrumentedConfig(),
) : InitModule by initModule {

    val openTelemetryModule: OpenTelemetryModule by lazy { OpenTelemetryModuleImpl(initModule = this) }

    fun getFakeClock(): FakeClock? = clock as? FakeClock
}
