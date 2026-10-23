package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.createCurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.injection.createOtelSdkConfig
import io.embrace.android.embracesdk.internal.injection.createRedactionFunction
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl

/**
 * [useKotlinSdk] selects the opentelemetry-kotlin implementation that backs the OTel module.
 * [clock] supplies every timestamp the recorded telemetry carries.
 */
internal class TelemetryDestinationHarness(
    useKotlinSdk: Boolean = false,
    clock: Clock = NormalizedIntervalClock(),
) {

    private val initServices = TestInitServices(clock)
    private val configService = BenchmarkConfigService(
        thresholdCheck = BehaviorThresholdCheck { initServices.uuidSource.createUuid() },
        remoteConfig = RemoteConfig(
            otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = if (useKotlinSdk) 100f else 0f),
        ),
    )
    private val openTelemetryClock = EmbClock(initServices.clock)

    val logSink: LogSink = LogSinkImpl()
    val spanRepository: SpanRepository = SpanRepository(initServices.errorHandler)

    private val otelSdkWrapper: OtelSdkWrapper by lazy {
        OtelSdkWrapper(
            otelClock = openTelemetryClock,
            configuration = createOtelSdkConfig(
                spanRepository = spanRepository,
                logSink = logSink,
                instrumentedConfig = initServices.instrumentedConfig,
                systemInfo = initServices.systemInfo,
                uuidSource = initServices.uuidSource,
                errorHandler = initServices.errorHandler,
            ),
            spanServiceProvider = { spanService },
            errorHandler = initServices.errorHandler,
            useKotlinSdk = configService.otelBehavior.shouldUseKotlinSdk(),
        )
    }

    private val dataValidator = DataValidator(
        bypassValidation = configService::isOnlyUsingOtelExporters,
        telemetryService = initServices.telemetryService,
    )

    val currentSessionPartSpan: CurrentSessionPartSpan = createCurrentSessionPartSpan(
        openTelemetryClock = openTelemetryClock,
        telemetryService = initServices.telemetryService,
        spanRepository = spanRepository,
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        embraceSpanFactorySupplier = { embraceSpanFactory },
        uuidSource = initServices.uuidSource,
        otelBehaviorSupplier = { configService.otelBehavior },
        customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
    )

    private val embraceSpanFactory = EmbraceSpanFactoryImpl(
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        dataValidator = dataValidator,
        stopCallback = currentSessionPartSpan::spanStopCallback,
        redactionFunction = createRedactionFunction(SensitiveKeysBehavior(configService.config.sensitiveKeys)),
        telemetryService = initServices.telemetryService,
        errorHandler = initServices.errorHandler,
    )

    val spanService: SpanService by lazy {
        SpanServiceImpl(
            spanRepository = spanRepository,
            canStartNewSpan = currentSessionPartSpan::canStartNewSpan,
            dataValidator = dataValidator,
            embraceSpanFactory = embraceSpanFactory,
            tracer = otelSdkWrapper.sdkTracer,
            openTelemetry = otelSdkWrapper.openTelemetryKotlin,
        )
    }

    init {
        currentSessionPartSpan.initializeService(0)
    }

    val destination: TelemetryDestination = TelemetryDestinationImpl(
        clock = initServices.clock,
        spanService = spanService,
        loggerSupplier = { otelSdkWrapper.sdkLogger },
        currentSessionPartSpan = currentSessionPartSpan,
    )

    /**
     * Builds a [SpanService] wired with the module's real tracer, OTel SDK, and span repository,
     * but where no per-session span limit is applied
     */
    fun createUncappedSpanService(): SpanService {
        val validator = DataValidator(telemetryService = NoopInternalTelemetryService)
        val factory = EmbraceSpanFactoryImpl(
            openTelemetryClock = EmbClock(initServices.clock),
            spanRepository = spanRepository,
            dataValidator = validator,
            telemetryService = NoopInternalTelemetryService,
            errorHandler = initServices.errorHandler,
        )
        return SpanServiceImpl(
            spanRepository = spanRepository,
            dataValidator = validator,
            canStartNewSpan = { _, _, _ -> true },
            embraceSpanFactory = factory,
            tracer = otelSdkWrapper.sdkTracer,
            openTelemetry = otelSdkWrapper.openTelemetryKotlin,
        )
    }

    private class TestInitServices(val clock: Clock) {
        val telemetryService: InternalTelemetryService = NoopInternalTelemetryService
        val errorHandler: ErrorHandler = ErrorHandlerImpl(clock)
        val systemInfo: SystemInfo = SystemInfo()
        val uuidSource: UuidSource = UuidSourceImpl()
        val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl
    }

    private object NoopInternalTelemetryService : InternalTelemetryService {
        override fun onPublicApiCalled(name: String) {
        }

        override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        }

        override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        }

        override fun getAndClearTelemetryAttributes(): Map<String, String> = emptyMap()
    }
}
