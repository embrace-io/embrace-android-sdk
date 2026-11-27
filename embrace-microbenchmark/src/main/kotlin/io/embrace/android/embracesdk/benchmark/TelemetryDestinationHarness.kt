package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.createCurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.injection.createOtelSdkConfig
import io.embrace.android.embracesdk.internal.injection.createRedactionFunction
import io.embrace.android.embracesdk.internal.injection.createStateTelemetry
import io.embrace.android.embracesdk.internal.injection.startStateReplay
import io.embrace.android.embracesdk.internal.logging.androidLogcat
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
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
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

/**
 * [useKotlinSdk] selects the opentelemetry-kotlin implementation that backs the OTel module.
 * [clock] supplies every timestamp the recorded telemetry carries.
 * [externalOtelConfig] holds the processors and exporters an app would add to the OTel module.
 * [eventMetadataProvider] supplies the attributes every log record is stamped with.
 * [config] is the resolved config the OTel module is put together from: with the app state data model's first step on, as
 * [io.embrace.android.embracesdk.internal.config.isAppStateEnabled] says, the harness makes the model's [stateTelemetry] and wires
 * it in as the SDK does, and with its second step on as well, spans are linked to state.
 */
internal class TelemetryDestinationHarness(
    useKotlinSdk: Boolean = false,
    val clock: Clock = NormalizedIntervalClock(),
    private val externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
    private val eventMetadataProvider: () -> Map<String, String> = { emptyMap() },
    config: EmbraceConfig = EmbraceConfig(),
) {

    private val initServices = TestInitServices(clock)
    val errorHandler: ErrorHandler = initServices.errorHandler
    val telemetryService: InternalTelemetryService = initServices.telemetryService
    val configService: ConfigService = BenchmarkConfigService(
        thresholdCheck = BehaviorThresholdCheck { initServices.uuidSource.createUuid() },
        remoteConfig = RemoteConfig(
            otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = if (useKotlinSdk) 100f else 0f),
        ),
        config = config,
    )

    /**
     * The bus state is reported on, which the recorder is attached to when the model is on.
     */
    val eventBus: EventBus = EventBus(initServices.errorHandler)

    /**
     * What ties telemetry to state, or null where the app state data model is off.
     */
    val stateTelemetry: StateTelemetry? = createStateTelemetry(
        configService = configService,
        clock = clock,
        telemetryService = initServices.telemetryService,
        externalOtelConfig = externalOtelConfig,
        eventBus = { eventBus },
    )
    private val openTelemetryClock = EmbClock(initServices.clock)

    val logSink: LogSink = LogSinkImpl(initServices.errorHandler)
    val spanRepository: SpanRepository = SpanRepository(initServices.errorHandler)

    private val otelSdkConfig: OtelSdkConfig = createOtelSdkConfig(
        spanRepository = spanRepository,
        logSink = logSink,
        instrumentedConfig = initServices.instrumentedConfig,
        systemInfo = initServices.systemInfo,
        uuidSource = initServices.uuidSource,
        errorHandler = initServices.errorHandler,
        externalOtelConfig = externalOtelConfig,
        eventMetadataProvider = eventMetadataProvider,
        stateTelemetry = stateTelemetry,
    )

    private val otelSdkWrapper: OtelSdkWrapper by lazy {
        OtelSdkWrapper(
            otelClock = openTelemetryClock,
            configuration = otelSdkConfig,
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
        errorHandler = initServices.errorHandler,
        spanRepository = spanRepository,
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        embraceSpanFactorySupplier = { embraceSpanFactory },
        uuidSource = initServices.uuidSource,
        otelBehaviorSupplier = { configService.otelBehavior },
        customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
    )

    /**
     * What a span is linked to state by, which is only where the model's second step is on, as the SDK wires it.
     */
    private val linkedStateTelemetry: StateTelemetry? = stateTelemetry?.takeIf { it.associationEnabled }

    private val embraceSpanFactory = EmbraceSpanFactoryImpl(
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        dataValidator = dataValidator,
        stopCallback = currentSessionPartSpan::spanStopCallback,
        redactionFunction = createRedactionFunction(SensitiveKeysBehavior(configService.config.sensitiveKeys)),
        telemetryService = initServices.telemetryService,
        errorHandler = initServices.errorHandler,
        stateTelemetry = linkedStateTelemetry,
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

    /**
     * Starts replaying state changes to the app's own log exporters of [externalOtelConfig] on [worker], as the SDK does as it is
     * put together, for a harness whose config has the model on. Nothing is replayed unless the model's first step is on and the
     * app has something that receives logs.
     */
    fun startReplay(worker: BackgroundWorker) {
        startStateReplay(
            stateTelemetry = checkNotNull(stateTelemetry) { "the app state data model is off" },
            externalOtelConfig = externalOtelConfig,
            loggerProvider = { otelSdkWrapper.sdkLogger },
            worker = { worker },
            processIdentifier = { PROCESS_IDENTIFIER },
            errorHandler = initServices.errorHandler,
            telemetryService = initServices.telemetryService,
            rootContextProvider = { otelSdkWrapper.openTelemetryKotlin.context.root() },
        )
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
            stateTelemetry = linkedStateTelemetry,
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
        val errorHandler: ErrorHandler = ErrorHandlerImpl(clock::now, ::androidLogcat)
        val systemInfo: SystemInfo = SystemInfo()
        val uuidSource: UuidSource = UuidSourceImpl()
        val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl
    }

    private companion object {
        const val PROCESS_IDENTIFIER = "benchmark-process"
    }
}

/**
 * An [InternalTelemetryService] that keeps nothing, so that tracking what the SDK reports costs nothing a benchmark measures.
 */
internal object NoopInternalTelemetryService : InternalTelemetryService {
    override fun onPublicApiCalled(name: String) {
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
    }

    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
    }

    override fun trackSlowCallback(name: String, durationMs: Long) {
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> = emptyMap()
}
