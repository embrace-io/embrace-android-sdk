package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.otel.config.getMaxTotalLinkCount
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetrySdk
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResource
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSdkLoggerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSdkTracerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanLimits
import io.embrace.opentelemetry.kotlin.tracing.Tracer

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
@OptIn(ExperimentalApi::class)
class OtelSdkWrapper(
    openTelemetryClock: OtelJavaClock,
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
                .addSpanProcessor(configuration.spanProcessor)
                .setSpanLimits(
                    OtelJavaSpanLimits
                        .getDefault()
                        .toBuilder()
                        .setMaxNumberOfEvents(limits.getMaxTotalEventCount())
                        .setMaxNumberOfAttributes(limits.getMaxTotalAttributeCount())
                        .setMaxNumberOfLinks(limits.getMaxTotalLinkCount())
                        .build()
                )
                .setClock(openTelemetryClock)
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
        configuration.resourceBuilder.build()
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
                        .addLogRecordProcessor(configuration.logProcessor)
                        .setClock(openTelemetryClock)
                        .build()
                )
                .build()
        }
    }

    @OptIn(ExperimentalApi::class)
    val kotlinApi: OpenTelemetry by lazy {
        io.embrace.opentelemetry.kotlin.k2j.OpenTelemetrySdk(sdk)
    }
}
