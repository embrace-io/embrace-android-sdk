package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracerProvider
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider

internal class OpenTelemetryModuleImpl(
    private val initModule: InitModule,
    override val openTelemetryClock: io.opentelemetry.sdk.common.Clock = EmbClock(
        embraceClock = initModule.clock
    ),
) : OpenTelemetryModule {

    override val spanRepository: SpanRepository by lazy {
        SpanRepository()
    }

    override val spanSink: SpanSink by lazy {
        SpanSinkImpl()
    }

    override val otelSdkConfig: OtelSdkConfig by lazy {
        OtelSdkConfig(
            spanSink = spanSink,
            logSink = logSink,
            sdkName = BuildConfig.LIBRARY_PACKAGE_NAME,
            sdkVersion = BuildConfig.VERSION_NAME,
            systemInfo = initModule.systemInfo,
            processIdentifierProvider = initModule.processIdentifierProvider
        )
    }

    private val otelSdkWrapper: OtelSdkWrapper by lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            try {
                OtelSdkWrapper(
                    openTelemetryClock = openTelemetryClock,
                    configuration = otelSdkConfig
                )
            } catch (exc: NoClassDefFoundError) {
                throw LinkageError(
                    "Please enable library desugaring in your project to use the Embrace SDK. " +
                        "This is required if you target API levels below 24. For instructions, please see " +
                        "https://developer.android.com/studio/write/java8-support#library-desugaring",
                    exc
                )
            }
        }
    }

    override val sdkTracer: Tracer by lazy {
        otelSdkWrapper.sdkTracer
    }

    private var sensitiveKeysBehavior: SensitiveKeysBehavior? = null

    override fun setupSensitiveKeysBehavior(sensitiveKeysBehavior: SensitiveKeysBehavior) {
        this.sensitiveKeysBehavior = sensitiveKeysBehavior
    }

    private val embraceSpanFactory: EmbraceSpanFactory by singleton {
        EmbraceSpanFactoryImpl(
            tracer = sdkTracer,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            redactionFunction = ::redactionFunction
        )
    }

    fun redactionFunction(key: String, value: String): String {
        return if (sensitiveKeysBehavior?.isSensitiveKey(key) == true) {
            REDACTED_LABEL
        } else {
            value
        }
    }

    override val currentSessionSpan: CurrentSessionSpan by lazy {
        CurrentSessionSpanImpl(
            openTelemetryClock = openTelemetryClock,
            telemetryService = initModule.telemetryService,
            spanRepository = spanRepository,
            spanSink = spanSink,
            embraceSpanFactorySupplier = { embraceSpanFactory }
        )
    }

    override val spanService: SpanService by singleton {
        EmbraceSpanService(
            spanRepository = spanRepository,
            currentSessionSpan = currentSessionSpan,
        ) { embraceSpanFactory }
    }

    override val embraceTracer: EmbraceTracer by singleton {
        EmbraceTracer(
            spanService = spanService,
        )
    }

    override val internalTracer: InternalTracer by lazy {
        InternalTracer(
            spanRepository = spanRepository,
            embraceTracer = embraceTracer,
            clock = initModule.clock,
        )
    }

    override val logger: Logger by lazy {
        otelSdkWrapper.getOpenTelemetryLogger()
    }

    override val logSink: LogSink by lazy {
        LogSinkImpl()
    }

    override val externalOpenTelemetry: OpenTelemetry by lazy {
        EmbOpenTelemetry(
            traceProviderSupplier = { externalTracerProvider }
        )
    }

    override val externalTracerProvider: TracerProvider by lazy {
        EmbTracerProvider(
            sdkTracerProvider = otelSdkWrapper.sdkTracerProvider,
            spanService = spanService,
            clock = openTelemetryClock,
        )
    }
}
