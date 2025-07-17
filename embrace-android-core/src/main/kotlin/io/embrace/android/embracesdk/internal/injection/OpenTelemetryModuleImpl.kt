package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
internal class OpenTelemetryModuleImpl(
    private val initModule: InitModule,
    override val openTelemetryClock: EmbClock = EmbClock(
        embraceClock = initModule.clock
    ),
) : OpenTelemetryModule {

    init {
        // Enforce the use of default ThreadLocal ContextStorage of the OTel Java to bypass SPI looking that violates Android strict mode
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
    }

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
            sessionIdProvider = { currentSessionSpan.getSessionId() },
            processIdentifierProvider = initModule.processIdentifierProvider
        )
    }

    override val otelSdkWrapper: OtelSdkWrapper by lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            try {
                OtelSdkWrapper(
                    otelClock = openTelemetryClock,
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

    private var sensitiveKeysBehavior: SensitiveKeysBehavior? = null

    override fun applyConfiguration(sensitiveKeysBehavior: SensitiveKeysBehavior, bypassValidation: Boolean) {
        this.sensitiveKeysBehavior = sensitiveKeysBehavior
        this.bypassLimitsValidation = bypassValidation
    }

    private var internalSpanStopCallback: ((spanId: String) -> Unit)? = null

    private var bypassLimitsValidation: Boolean = false

    private val dataValidator: DataValidator = DataValidator(bypassValidation = ::bypassLimitsValidation)

    private val embraceSpanFactory: EmbraceSpanFactory by singleton {
        EmbraceSpanFactoryImpl(
            tracer = otelSdkWrapper.sdkTracer,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            stopCallback = ::spanStopCallbackWrapper,
            redactionFunction = ::redactionFunction
        )
    }

    override val currentSessionSpan: CurrentSessionSpan by lazy {
        CurrentSessionSpanImpl(
            openTelemetryClock = openTelemetryClock,
            telemetryService = initModule.telemetryService,
            spanRepository = spanRepository,
            spanSink = spanSink,
            embraceSpanFactorySupplier = { embraceSpanFactory }
        ).also {
            internalSpanStopCallback = it::spanStopCallback
        }
    }

    override val spanService: SpanService by singleton {
        EmbraceSpanService(
            spanRepository = spanRepository,
            canStartNewSpan = currentSessionSpan::canStartNewSpan,
            initCallback = currentSessionSpan::initializeService,
            dataValidator = dataValidator
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

    override val logSink: LogSink by lazy {
        LogSinkImpl()
    }

    fun redactionFunction(key: String, value: String): String {
        return if (sensitiveKeysBehavior?.isSensitiveKey(key) == true) {
            REDACTED_LABEL
        } else {
            value
        }
    }

    fun spanStopCallbackWrapper(spanId: String) {
        internalSpanStopCallback?.invoke(spanId)
    }
}
