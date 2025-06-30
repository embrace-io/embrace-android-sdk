package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalLinkCount
import io.embrace.android.embracesdk.internal.otel.impl.EmbOtelJavaClock
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanProcessor
import io.embrace.android.embracesdk.internal.otel.wrapper.KotlinLogRecordExportWrapper
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetrySdk
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResource
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSdkLoggerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSdkTracerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanLimits
import io.embrace.opentelemetry.kotlin.compatWithOtelJava
import io.embrace.opentelemetry.kotlin.kotlinApi
import io.embrace.opentelemetry.kotlin.tracing.Tracer

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
@OptIn(ExperimentalApi::class)
class OtelSdkWrapper(
    otelClock: OtelJavaClock,
    configuration: OtelSdkConfig,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
) {
    init {
        // Enforce the use of default ThreadLocal ContextStorage of the OTel Java to bypass SPI looking that violates Android strict mode
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
    }

    val sdkTracerProvider: OtelJavaSdkTracerProvider by lazy {
        EmbTrace.trace("otel-tracer-provider-init") {
            OtelJavaSdkTracerProvider
                .builder()
                .addResource(resource)
                .addSpanProcessor(configuration.otelJavaSpanProcessor)
                .setSpanLimits(
                    OtelJavaSpanLimits
                        .getDefault()
                        .toBuilder()
                        .setMaxNumberOfEvents(limits.getMaxTotalEventCount())
                        .setMaxNumberOfAttributes(limits.getMaxTotalAttributeCount())
                        .setMaxNumberOfLinks(limits.getMaxTotalLinkCount())
                        .build()
                )
                .setClock(otelClock)
                .build()
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

    private val resource: OtelJavaResource by lazy {
        configuration.otelJavaResourceBuilder.build()
    }

    private val sdk: OtelJavaOpenTelemetrySdk by lazy {
        EmbTrace.trace("otel-sdk-init") {
            OtelJavaOpenTelemetrySdk
                .builder()
                .setTracerProvider(sdkTracerProvider)
                .setLoggerProvider(
                    OtelJavaSdkLoggerProvider
                        .builder()
                        .addResource(resource)
                        .addLogRecordProcessor(configuration.otelJavaLogProcessor)
                        .setClock(otelClock)
                        .build()
                )
                .build()
        }
    }

    val kotlinApi: OpenTelemetry by lazy {
        OpenTelemetryInstance.compatWithOtelJava(sdk)
    }

    /**
     * Creates an instance of opentelemetry-kotlin using the Kotlin API's DSL, rather than the opentelemetry-java
     * API.
     */
    @Suppress("unused", "UNREACHABLE_CODE")
    private val kotlinApiViaDsl: OpenTelemetry by lazy {
        OpenTelemetryInstance.kotlinApi(
            loggerProvider = {
                resource(configuration.resourceAction)
                addLogRecordProcessor(
                    DefaultLogRecordProcessor(
                        KotlinLogRecordExportWrapper(configuration.otelJavaLogRecordExporter)
                    )
                )
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
                        TODO(),
                        configuration.processIdentifier
                    )
                )
            },
            clock = otelClock as EmbOtelJavaClock
        )
    }
}
