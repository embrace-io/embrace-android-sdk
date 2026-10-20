package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl
import io.embrace.android.embracesdk.internal.utils.UuidSource

class FakeInitModule(
    clock: Clock = FakeClock(),
    logger: InternalLogger = FakeInternalLogger(),
    systemInfo: SystemInfo = SystemInfo(
        osVersion = "99.0.0",
        deviceManufacturer = "Fake Manufacturer",
        deviceModel = "Phake Phone Phive"
    ),
    fakeTelemetryService: InternalTelemetryService? = null,
    override val uuidSource: UuidSource = TestUuidSource(),
    val preStartServices: PreStartServices = PreStartServices(
        logger = logger,
        clock = clock,
        systemInfo = systemInfo,
        telemetryService = fakeTelemetryService ?: InternalTelemetryServiceImpl(systemInfo = systemInfo),
    ),
    private val initModule: InitModule = InitModuleImpl(
        preStartServices = preStartServices,
        uuidSource = uuidSource,
    ),
    override var instrumentedConfig: InstrumentedConfig = FakeInstrumentedConfig(),
    private val remoteConfig: RemoteConfig = RemoteConfig(),
    private val otelSdkMode: OtelSdkMode,
) : InitModule by initModule {

    /**
     * Resolves the OTel behavior from [remoteConfig], with the OTel SDK implementation selected by [otelSdkMode].
     */
    val openTelemetryModule: OpenTelemetryModule by lazy {
        OpenTelemetryModuleImpl(
            initModule = this,
            configService = FakeConfigService(
                otelBehavior = createOtelBehavior(
                    remoteCfg = remoteConfig.copy(
                        otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = if (otelSdkMode.useKotlinSdk) 100f else 0f),
                    ),
                ),
            ),
            externalOtelConfig = preStartServices.externalOtelConfig,
        )
    }

    fun getFakeClock(): FakeClock? = clock as? FakeClock
}
