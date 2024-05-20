package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.Systrace
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
internal class OpenTelemetrySdk(
    openTelemetryClock: Clock,
    configuration: OpenTelemetryConfiguration
) {
    val coreTracerProvider: SdkTracerProvider = Systrace.traceSynchronous("otel-tracer-provider-init") {
        SdkTracerProvider
            .builder()
            .addResource(configuration.resource)
            .addSpanProcessor(configuration.spanProcessor)
            .setClock(openTelemetryClock)
            .build()
    }

    private val sdk: OpenTelemetrySdk = Systrace.traceSynchronous("otel-sdk-init") {
        OpenTelemetrySdk
            .builder()
            .setTracerProvider(coreTracerProvider)
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

    private val tracer: Tracer = Systrace.traceSynchronous("otel-tracer-init") {
        sdk.getTracer(configuration.embraceServiceName, configuration.embraceVersionName)
    }

    private val logger: Logger by lazy {
        Systrace.traceSynchronous("otel-logger-init") {
            sdk.logsBridge.loggerBuilder(configuration.embraceServiceName).build()
        }
    }

    fun getOpenTelemetryTracer(): Tracer = tracer

    fun getOpenTelemetryLogger(): Logger = logger
}
