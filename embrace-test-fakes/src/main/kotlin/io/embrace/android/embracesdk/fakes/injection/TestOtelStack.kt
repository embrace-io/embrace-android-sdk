package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.createCurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.injection.createOtelSdkConfig
import io.embrace.android.embracesdk.internal.injection.createRedactionFunction
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.spans.TracingApiDelegate
import io.embrace.android.embracesdk.spans.TracingApi

class TestOtelStack(
    initServices: TestInitServices,
    configService: ConfigService,
    externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
) {
    private val openTelemetryClock = EmbClock(initServices.clock)

    val spanRepository: SpanRepository = SpanRepository(initServices.errorHandler)

    val logSink: LogSink = LogSinkImpl()

    val otelSdkConfig: OtelSdkConfig by lazy {
        createOtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            instrumentedConfig = initServices.instrumentedConfig,
            systemInfo = initServices.systemInfo,
            uuidSource = initServices.uuidSource,
            errorHandler = initServices.errorHandler,
            externalOtelConfig = externalOtelConfig,
        )
    }

    val otelSdkWrapper: OtelSdkWrapper by lazy {
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
        spanRepository = spanRepository,
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        embraceSpanFactorySupplier = { embraceSpanFactory },
        uuidSource = initServices.uuidSource,
        otelBehaviorSupplier = { configService.otelBehavior },
        customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
    )

    private val embraceSpanFactory: EmbraceSpanFactory = EmbraceSpanFactoryImpl(
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

    val tracingApi: TracingApi by lazy { TracingApiDelegate(spanService) }
}
