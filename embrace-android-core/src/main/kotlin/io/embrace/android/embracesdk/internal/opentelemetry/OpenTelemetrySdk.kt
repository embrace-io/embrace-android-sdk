package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.spans.getMaxTotalAttributeCount
import io.embrace.android.embracesdk.internal.spans.getMaxTotalEventCount
import io.embrace.android.embracesdk.internal.spans.getMaxTotalLinkCount
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanLimits

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
internal class OpenTelemetrySdk(
    openTelemetryClock: Clock,
    configuration: OpenTelemetryConfiguration,
    limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
) {
    init {
        // Enforce the use of default ThreadLocal ContextStorage of the OTel Java to bypass SPI looking that violates Android strict mode
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default")
    }

    val sdkTracerProvider: SdkTracerProvider by lazy {
        Systrace.traceSynchronous("otel-tracer-provider-init") {
            SdkTracerProvider
                .builder()
                .addResource(resource)
                .addSpanProcessor(configuration.spanProcessor)
                .setSpanLimits(
                    SpanLimits
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
        Systrace.traceSynchronous("otel-tracer-init") {
            sdk.getTracer(configuration.embraceSdkName, configuration.embraceSdkVersion)
        }
    }

    fun getOpenTelemetryLogger(): Logger = logger

    private val resource: Resource by lazy {
        configuration.resourceBuilder.build()
    }

    private val sdk: OpenTelemetrySdk by lazy {
        Systrace.traceSynchronous("otel-sdk-init") {
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(sdkTracerProvider)
                .setLoggerProvider(
                    SdkLoggerProvider
                        .builder()
                        .addResource(resource)
                        .addLogRecordProcessor(configuration.logProcessor)
                        .setClock(openTelemetryClock)
                        .build()
                )
                .build()
        }
    }

    private val logger: Logger by lazy {
        Systrace.traceSynchronous("otel-logger-init") {
            sdk.logsBridge.loggerBuilder(configuration.embraceSdkName).build()
        }
    }
}
