package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalLinkCount
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.impl.EmbLoggerProvider
import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracerProvider
import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.export.createCompositeLogRecordProcessor
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.createCompositeSpanProcessor

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
@OptIn(ExperimentalApi::class)
class OtelSdkWrapper(
    otelClock: EmbClock,
    configuration: OtelSdkConfig,
    spanService: SpanService,
    eventService: EventService,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
    val useKotlinSdk: Boolean,
) {

    init {
        if (!useKotlinSdk) {
            // Enforce the use of default OTel Java SDK ThreadLocal ContextStorage to bypass SPI looking that violates Android strict mode
            System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
        }
    }

    val sdkTracer: Tracer by lazy {
        EmbTrace.trace("otel-tracer-init") {
            kotlinApi.tracerProvider.getTracer(
                name = configuration.sdkName,
                version = configuration.sdkVersion
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

    @Suppress("DEPRECATION")
    private val kotlinApi: OpenTelemetry by lazy {
        createSdkOtelInstance(
            useKotlinSdk = useKotlinSdk,
            tracerProvider = {
                resource(attributes = configuration.resourceAction)
                spanLimits {
                    eventCountLimit = limits.getMaxTotalEventCount()
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                    linkCountLimit = limits.getMaxTotalLinkCount()
                }
                addSpanProcessor(
                    createCompositeSpanProcessor(
                        listOf(configuration.spanProcessor) + configuration.getExternalSpanProcessors()
                    )
                )
            },
            loggerProvider = {
                resource(attributes = configuration.resourceAction)
                logLimits {
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                }
                addLogRecordProcessor(
                    createCompositeLogRecordProcessor(
                        listOf(configuration.logRecordProcessor) + configuration.getExternalLogRecordProcessors()
                    )
                )
            },
            clock = otelClock,
        )
    }

    val openTelemetryKotlin: OpenTelemetry by lazy {
        EmbOpenTelemetry(
            impl = kotlinApi,
            traceProviderSupplier = { EmbTracerProvider(kotlinApi, spanService, otelClock, useKotlinSdk) },
            loggerProviderSupplier = { EmbLoggerProvider(kotlinApi, eventService) }
        )
    }
}
