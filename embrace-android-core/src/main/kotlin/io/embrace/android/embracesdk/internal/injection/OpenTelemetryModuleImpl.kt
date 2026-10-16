package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpanImpl
import io.embrace.android.embracesdk.internal.spans.TracingApiDelegate
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.spans.TracingApi

class OpenTelemetryModuleImpl(
    private val initModule: InitModule,
    private val configService: ConfigService,
    private val externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
    private val sessionIdsProvider: () -> SessionIdsProvider? = { null },
    private val userIdProvider: () -> String? = { null },
    private val eventMetadataProvider: () -> Map<String, String> = { emptyMap() },
    private val openTelemetryClock: EmbClock = EmbClock(
        embraceClock = initModule.clock,
    ),
) : OpenTelemetryModule {

    private val sensitiveKeysBehavior = SensitiveKeysBehavior(configService.config.sensitiveKeys)

    override val spanRepository: SpanRepository = SpanRepository()

    override val logSink: LogSink = LogSinkImpl()

    override val otelSdkConfig: OtelSdkConfig by lazy {
        OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = BuildConfig.LIBRARY_PACKAGE_NAME,
            sdkVersion = BuildConfig.VERSION_NAME,
            appVersion = initModule.instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
            packageName = initModule.instrumentedConfig.project.getPackageName() ?: "UNKNOWN",
            systemInfo = initModule.systemInfo,
            uuidSource = initModule.uuidSource,
            sessionIdsProvider = sessionIdsProvider,
            userIdProvider = userIdProvider,
            eventMetadataProvider = eventMetadataProvider,
            processIdentifierProvider = IdGenerator.Companion::generateLaunchInstanceId,
            externalConfig = externalOtelConfig,
        )
    }

    override val otelSdkWrapper: OtelSdkWrapper by lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            OtelSdkWrapper(
                otelClock = openTelemetryClock,
                configuration = otelSdkConfig,
                spanServiceProvider = { spanService },
                errorHandler = initModule.logger,
                useKotlinSdk = configService.otelBehavior.shouldUseKotlinSdk(),
            )
        }
    }

    private val dataValidator: DataValidator = DataValidator(
        bypassValidation = configService::isOnlyUsingOtelExporters,
        telemetryService = initModule.telemetryService,
    )

    private val currentSessionPartSpanImpl: CurrentSessionPartSpanImpl = CurrentSessionPartSpanImpl(
        openTelemetryClock = openTelemetryClock,
        telemetryService = initModule.telemetryService,
        spanRepository = spanRepository,
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        embraceSpanFactorySupplier = { embraceSpanFactory },
        uuidSource = initModule.uuidSource,
        otelBehaviorSupplier = { configService.otelBehavior },
        customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
    )

    override val currentSessionPartSpan: CurrentSessionPartSpan = currentSessionPartSpanImpl

    private val embraceSpanFactory: EmbraceSpanFactory = EmbraceSpanFactoryImpl(
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        dataValidator = dataValidator,
        stopCallback = currentSessionPartSpanImpl::spanStopCallback,
        redactionFunction = ::redact,
        telemetryService = initModule.telemetryService,
    )

    override val spanService: SpanService by lazy {
        SpanServiceImpl(
            spanRepository = spanRepository,
            canStartNewSpan = currentSessionPartSpan::canStartNewSpan,
            dataValidator = dataValidator,
            embraceSpanFactory = embraceSpanFactory,
            tracer = otelSdkWrapper.sdkTracer,
            openTelemetry = otelSdkWrapper.openTelemetryKotlin,
        )
    }

    override val tracingApi: TracingApi by lazy {
        TracingApiDelegate(
            spanService = spanService,
        )
    }

    private fun redact(key: String, value: String): String {
        return if (sensitiveKeysBehavior.isSensitiveKey(key)) {
            REDACTED_LABEL
        } else {
            value
        }
    }
}
