package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.lazyOkHttpClient
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryServiceImpl
import io.embrace.android.embracesdk.internal.utils.UuidSource
import okhttp3.OkHttpClient

/**
 * The services the SDK is given before it starts, built from fakes where tests need control over them.
 */
class TestInitServices(
    clock: Clock = FakeClock(),
    errorHandler: ErrorHandler = FakeErrorHandler(delegate = ErrorHandlerImpl(clock)),
    fakeTelemetryService: InternalTelemetryService? = null,
    val uuidSource: UuidSource = TestUuidSource(),
    private val remoteConfig: RemoteConfig = RemoteConfig(),
    private val otelSdkMode: OtelSdkMode,
) {
    val systemInfo: SystemInfo = SystemInfo(
        osVersion = "99.0.0",
        deviceManufacturer = "Fake Manufacturer",
        deviceModel = "Phake Phone Phive"
    )
    val preStartServices: PreStartServices = PreStartServices(
        errorHandler = errorHandler,
        clock = clock,
        systemInfo = systemInfo,
        telemetryService = fakeTelemetryService ?: InternalTelemetryServiceImpl(systemInfo = systemInfo),
    )
    val clock: Clock = preStartServices.clock
    val errorHandler: ErrorHandler = preStartServices.errorHandler
    val telemetryService: InternalTelemetryService = preStartServices.telemetryService
    val startupClassifier: StartupClassifier = StartupClassifierImpl()
    val jsonSerializer: PlatformSerializer = EmbraceSerializer()
    val okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient()
    var instrumentedConfig: InstrumentedConfig = FakeInstrumentedConfig()

    /**
     * Resolves the OTel behavior from [remoteConfig], with the OTel SDK implementation selected by [otelSdkMode].
     */
    val otelStack: TestOtelStack by lazy {
        TestOtelStack(
            initServices = this,
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
