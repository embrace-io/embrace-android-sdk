package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.BuildConfig
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Wrapper that instantiates a copy of the OpenTelemetry SDK configured with the appropriate settings and the given components so
 * the Embrace SDK can hook into its lifecycle. From this, the Embrace SDK can obtain an implementations of the OpenTelemetry API to
 * create OpenTelemetry primitives that it can use internally or export to any OpenTelemetry Collectors.
 */
internal class OpenTelemetrySdk(
    openTelemetryClock: Clock,
    spanProcessor: SpanProcessor
) {
    private val sdk = OpenTelemetrySdk
        .builder()
        .setTracerProvider(
            SdkTracerProvider
                .builder()
                .addSpanProcessor(spanProcessor)
                .setClock(openTelemetryClock)
                .build()
        ).build()

    private val tracer = sdk.getTracer(BuildConfig.LIBRARY_PACKAGE_NAME, BuildConfig.VERSION_NAME)

    fun getOpenTelemetryTracer(): Tracer = tracer
}
