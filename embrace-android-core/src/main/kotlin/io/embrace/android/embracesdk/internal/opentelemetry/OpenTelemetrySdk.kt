package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.MAX_SYSTEM_EVENT_COUNT
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanImpl.Companion.MAX_TOTAL_ATTRIBUTE_COUNT
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanLimits

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
public class OpenTelemetrySdk(
    openTelemetryClock: Clock,
    configuration: OpenTelemetryConfiguration
) {
    public val sdkTracerProvider: SdkTracerProvider by lazy {
        Systrace.traceSynchronous("otel-tracer-provider-init") {
            SdkTracerProvider
                .builder()
                .addResource(configuration.resource)
                .addSpanProcessor(configuration.spanProcessor)
                .setSpanLimits(
                    SpanLimits
                        .getDefault()
                        .toBuilder()
                        .setMaxNumberOfEvents(MAX_SYSTEM_EVENT_COUNT)
                        .setMaxNumberOfAttributes(MAX_TOTAL_ATTRIBUTE_COUNT)
                        .build()
                )
                .setClock(openTelemetryClock)
                .build()
        }
    }

    public val sdkTracer: Tracer by lazy {
        Systrace.traceSynchronous("otel-tracer-init") {
            sdk.getTracer(configuration.embraceSdkName, configuration.embraceSdkVersion)
        }
    }

    public fun getOpenTelemetryLogger(): Logger = logger

    private val sdk: OpenTelemetrySdk by lazy {
        Systrace.traceSynchronous("otel-sdk-init") {
            OpenTelemetrySdk
                .builder()
                .setTracerProvider(sdkTracerProvider)
                .setLoggerProvider(
                    SdkLoggerProvider
                        .builder()
                        .addResource(configuration.resource)
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
