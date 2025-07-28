package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalLinkCount
import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracerProvider
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.compatWithOtelKotlin
import io.embrace.opentelemetry.kotlin.kotlinApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.tracing.Tracer

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
@OptIn(ExperimentalApi::class)
class OtelSdkWrapper(
    otelClock: Clock,
    configuration: OtelSdkConfig,
    spanService: SpanService,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
) {
    init {
        // Enforce the use of default ThreadLocal ContextStorage of the OTel Java to bypass SPI looking that violates Android strict mode
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
    }

    val sdkTracer: Tracer by lazy {
        EmbTrace.trace("otel-tracer-init") {
            kotlinApi.tracerProvider.getTracer(
                name = configuration.sdkName,
                version = configuration.sdkVersion
            )
        }
    }

    val logger: Logger by lazy {
        EmbTrace.trace("otel-logger-init") {
            kotlinApi.loggerProvider.getLogger(
                name = configuration.sdkName,
                version = configuration.sdkVersion,
            )
        }
    }

    val kotlinApi: OpenTelemetry by lazy {
        OpenTelemetryInstance.kotlinApi(
            loggerProvider = {
                resource(configuration.resourceAction)
                addLogRecordProcessor(
                    DefaultLogRecordProcessor(configuration.logRecordExporter)
                )
                logLimits {
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                }
            },
            tracerProvider = {
                resource(configuration.resourceAction)
                spanLimits {
                    eventCountLimit = limits.getMaxTotalEventCount()
                    attributeCountLimit = limits.getMaxTotalAttributeCount()
                    linkCountLimit = limits.getMaxTotalLinkCount()
                }
                addSpanProcessor(
                    DefaultSpanProcessor(
                        configuration.spanExporter,
                        configuration.processIdentifier
                    )
                )
                addSpanProcessor(configuration.spanProcessor)
            },
            clock = otelClock
        )
    }

    val openTelemetryJava: OtelJavaOpenTelemetry by lazy {
        OpenTelemetryInstance.compatWithOtelKotlin(
            EmbOpenTelemetry(kotlinApi) {
                EmbTracerProvider(kotlinApi.tracerProvider, spanService, otelClock)
            }
        )
    }
}
