package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalLinkCount
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracerProvider
import io.embrace.android.embracesdk.internal.otel.logs.PrivateTelemetryFilteringLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.spans.PrivateTelemetryFilteringSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.export.compositeLogRecordProcessor
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
class OtelSdkWrapper(
    otelClock: Clock,
    configuration: OtelSdkConfig,
    spanService: SpanService,
    errorHandler: InternalErrorHandler,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
    val useKotlinSdk: Boolean,
) {

    init {
        // Enforce the use of default OTel Java SDK ThreadLocal ContextStorage to bypass SPI lookup that violates Android strict mode.
        // This applies even when the Kotlin SDK is used, as the Java context API is still reached via getJavaOpenTelemetry()
        // and any customer code that uses OTel Java directly.
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
    }

    val sdkTracer: Tracer by lazy {
        EmbTrace.trace(sectionName = "otel-tracer-init", recordDuration = true) {
            kotlinApi.tracerProvider.getTracer(
                name = configuration.sdkName,
                version = configuration.sdkVersion,
            )
        }
    }

    val sdkLogger: Logger by lazy {
        EmbTrace.trace("otel-logger-init") {
            kotlinApi.loggerProvider.getLogger(
                name = configuration.sdkName,
                version = configuration.sdkVersion,
            )
        }
    }

    private val kotlinApi: OpenTelemetry by lazy {
        try {
            createOtelInstance(otelClock, configuration, limits, errorHandler)
        } catch (exc: NoClassDefFoundError) {
            throw LinkageError(
                "Please enable library desugaring in your project to use the Embrace SDK. " +
                    "This is required if you target API levels below 24. For instructions, please see " +
                    "https://developer.android.com/studio/write/java8-support#library-desugaring",
                exc,
            )
        }
    }

    @Suppress("DEPRECATION", "SpreadOperator")
    private fun createOtelInstance(
        otelClock: Clock,
        configuration: OtelSdkConfig,
        limits: OtelLimitsConfig,
        errorHandler: InternalErrorHandler,
    ): OpenTelemetry =
        createSdkOtelInstance(
            useKotlinSdk = useKotlinSdk,
            tracerProvider = {
                resource(attributes = configuration.resourceAction)
                spanLimits {
                    eventCountLimit = limits.getMaxTotalEventCount()
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                    linkCountLimit = limits.getMaxTotalLinkCount()
                }
                export {
                    val processors = listOf(configuration.spanProcessor) + configuration.getExternalSpanProcessors()
                        .map(::PrivateTelemetryFilteringSpanProcessor)
                    compositeSpanProcessor(*processors.toTypedArray())
                }
            },
            loggerProvider = {
                resource(attributes = configuration.resourceAction)
                logLimits {
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                }
                export {
                    val processors = listOf(configuration.logRecordProcessor) + configuration.getExternalLogRecordProcessors()
                        .map(::PrivateTelemetryFilteringLogRecordProcessor)
                    compositeLogRecordProcessor(*processors.toTypedArray())
                }
            },
            clock = otelClock,
            errorHandler = OtelSdkErrorHandler(errorHandler),
        )

    val openTelemetryKotlin: OpenTelemetry by lazy {
        EmbOpenTelemetry(
            impl = kotlinApi,
            traceProviderSupplier = { EmbTracerProvider(kotlinApi, spanService, otelClock) },
        )
    }
}
