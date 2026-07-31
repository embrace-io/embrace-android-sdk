package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
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
    private val openTelemetryClock: EmbClock = EmbClock(
        embraceClock = initModule.clock,
    ),
) : OpenTelemetryModule {

    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId
    private var storedSessionIdsProvider: SessionIdsProvider? = null
    private var storedUserIdProvider: (() -> String?)? = null
    private var storedEventMetadataProvider: (() -> Map<String, String>)? = null
    private var otelBehavior: OtelBehavior? = null
    private var sensitiveKeysBehavior: SensitiveKeysBehavior? = null
    private var internalSpanStopCallback: ((spanId: String) -> Unit)? = null
    private var bypassLimitsValidation: Boolean = false

    override val spanRepository: SpanRepository = SpanRepository()

    override val logSink: LogSink = LogSinkImpl()

    override val otelSdkConfig: OtelSdkConfig by lazy {
        OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = "embrace-android-sdk",
            sdkVersion = BuildConfig.VERSION_NAME,
            appVersion = initModule.instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
            packageName = initModule.instrumentedConfig.project.getPackageName() ?: "UNKNOWN",
            systemInfo = initModule.systemInfo,
            uuidSource = initModule.uuidSource,
            sessionIdsProvider = { storedSessionIdsProvider },
            userIdProvider = { storedUserIdProvider?.invoke() },
            eventMetadataProvider = { storedEventMetadataProvider?.invoke() ?: emptyMap() },
            processIdentifierProvider = processIdentifierProvider,
            resourceAttributeOverrideEnabled = { otelBehavior?.isResourceAttributeOverrideEnabled() ?: false },
        )
    }

    override val otelSdkWrapper: OtelSdkWrapper by lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            try {
                OtelSdkWrapper(
                    otelClock = openTelemetryClock,
                    configuration = otelSdkConfig,
                    spanService = spanService,
                    // adding guard in case this is accessed before we fetch the config
                    useKotlinSdk = otelBehavior?.shouldUseKotlinSdk() ?: false,
                )
            } catch (exc: NoClassDefFoundError) {
                throw LinkageError(
                    "Please enable library desugaring in your project to use the Embrace SDK. " +
                        "This is required if you target API levels below 24. For instructions, please see " +
                        "https://developer.android.com/studio/write/java8-support#library-desugaring",
                    exc,
                )
            }
        }
    }

    override fun applyConfiguration(sensitiveKeysBehavior: SensitiveKeysBehavior, bypassValidation: Boolean, otelBehavior: OtelBehavior) {
        this.sensitiveKeysBehavior = sensitiveKeysBehavior
        this.bypassLimitsValidation = bypassValidation
        this.otelBehavior = otelBehavior
    }

    override fun setSessionIdsProvider(sessionIdsProvider: SessionIdsProvider) {
        storedSessionIdsProvider = sessionIdsProvider
    }

    override fun setUserIdProvider(userIdProvider: () -> String?) {
        storedUserIdProvider = userIdProvider
    }

    override fun setEventMetadataProvider(eventMetadataProvider: () -> Map<String, String>) {
        storedEventMetadataProvider = eventMetadataProvider
    }

    private val dataValidator: DataValidator = DataValidator(
        bypassValidation = ::bypassLimitsValidation,
        telemetryService = initModule.telemetryService,
    )

    private val embraceSpanFactory: EmbraceSpanFactory = EmbraceSpanFactoryImpl(
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        dataValidator = dataValidator,
        stopCallback = ::spanStopCallbackWrapper,
        redactionFunction = ::redactionFunction,
        telemetryService = initModule.telemetryService,
    )

    override val currentSessionPartSpan: CurrentSessionPartSpan = CurrentSessionPartSpanImpl(
        openTelemetryClock = openTelemetryClock,
        telemetryService = initModule.telemetryService,
        spanRepository = spanRepository,
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
        embraceSpanFactorySupplier = { embraceSpanFactory },
        uuidSource = initModule.uuidSource,
    ).also {
        internalSpanStopCallback = it::spanStopCallback
    }

    override val spanService: SpanService = SpanServiceImpl(
        spanRepository = spanRepository,
        canStartNewSpan = currentSessionPartSpan::canStartNewSpan,
        initCallback = currentSessionPartSpan::initializeService,
        dataValidator = dataValidator,
        embraceSpanFactory = embraceSpanFactory,
        // supplied lazily (otelSdkWrapper is constructed with a reference to this service)
        tracerSupplier = { otelSdkWrapper.sdkTracer },
        openTelemetrySupplier = { otelSdkWrapper.openTelemetryKotlin },
    )

    override val tracingApi: TracingApi = TracingApiDelegate(
        spanService = spanService,
    )

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
