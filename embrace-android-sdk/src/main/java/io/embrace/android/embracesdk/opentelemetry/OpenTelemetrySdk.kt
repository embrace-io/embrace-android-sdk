package io.embrace.android.embracesdk.opentelemetry

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.ResourceAttributes

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
internal class OpenTelemetrySdk(
    openTelemetryClock: Clock,
    configuration: OpenTelemetryConfiguration
) {
    private val resource: Resource = Resource.getDefault().toBuilder()
        .put(ResourceAttributes.SERVICE_NAME, configuration.serviceName)
        .put(ResourceAttributes.SERVICE_VERSION, configuration.serviceVersion)
        .build()

    private val sdk = OpenTelemetrySdk
        .builder()
        .setTracerProvider(
            SdkTracerProvider
                .builder()
                .addResource(resource)
                .addSpanProcessor(configuration.spanProcessor)
                .setClock(openTelemetryClock)
                .build()
        ).build()

    private val tracer = sdk.getTracer(configuration.serviceName, configuration.serviceVersion)

    fun getOpenTelemetryTracer(): Tracer = tracer
}
