package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.opentelemetry.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.opentelemetry.EmbTracerProvider
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetrySdk
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer

internal class OpenTelemetryModuleImpl(
    private val initModule: InitModule,
    override val openTelemetryClock: io.opentelemetry.sdk.common.Clock = OpenTelemetryClock(
        embraceClock = initModule.clock
    ),
) : OpenTelemetryModule {

    override val spanRepository: SpanRepository by lazy {
        SpanRepository()
    }

    override val spanSink: SpanSink by lazy {
        SpanSinkImpl()
    }

    override val openTelemetryConfiguration: OpenTelemetryConfiguration by lazy {
        OpenTelemetryConfiguration(
            spanSink = spanSink,
            logSink = logSink,
            systemInfo = initModule.systemInfo,
            processIdentifierProvider = initModule.processIdentifierProvider
        )
    }

    private val openTelemetrySdk: OpenTelemetrySdk by lazy {
        Systrace.traceSynchronous("otel-sdk-wrapper-init") {
            try {
                OpenTelemetrySdk(
                    openTelemetryClock = openTelemetryClock,
                    configuration = openTelemetryConfiguration
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
        openTelemetrySdk.sdkTracer
    }

    private var sensitiveKeysBehavior: SensitiveKeysBehavior? = null

    override fun setupSensitiveKeysBehavior(sensitiveKeysBehavior: SensitiveKeysBehavior) {
        this.sensitiveKeysBehavior = sensitiveKeysBehavior
        embraceSpanFactory.setupSensitiveKeysBehavior(sensitiveKeysBehavior)
    }

    private val embraceSpanFactory: EmbraceSpanFactory by singleton {
        EmbraceSpanFactoryImpl(
            tracer = sdkTracer,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            sensitiveKeysBehavior = sensitiveKeysBehavior
        )
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
            clock = initModule.clock,
            spanService = spanService,
        )
    }

    override val internalTracer: InternalTracer by lazy {
        InternalTracer(
            spanRepository = spanRepository,
            embraceTracer = embraceTracer
        )
    }

    override val logger: Logger by lazy {
        openTelemetrySdk.getOpenTelemetryLogger()
    }

    override val logSink: LogSink by lazy {
        LogSinkImpl()
    }

    override val externalOpenTelemetry: OpenTelemetry by lazy {
        EmbOpenTelemetry(
            traceProviderSupplier = { externalTracerProvider }
        )
    }

    override val externalTracerProvider: EmbTracerProvider by lazy {
        EmbTracerProvider(
            sdkTracerProvider = openTelemetrySdk.sdkTracerProvider,
            spanService = spanService,
            clock = openTelemetryClock,
        )
    }
}
