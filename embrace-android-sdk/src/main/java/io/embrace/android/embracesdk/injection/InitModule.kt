package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider

/**
 * A module of components and services required at [EmbraceImpl] instantiation time, i.e. before the SDK evens starts
 */
internal interface InitModule {
    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    val clock: Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    val telemetryService: TelemetryService

    val spansSink: SpansSink

    val openTelemetrySdk: OpenTelemetry

    val tracer: Tracer

    val currentSessionSpan: CurrentSessionSpan

    /**
     * Service to log traces
     */
    val spansService: SpansService

    /**
     * Implementation of public tracing API
     */
    val embraceTracer: EmbraceTracer
}

internal class InitModuleImpl(
    override val clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    openTelemetryClock: OpenTelemetryClock = OpenTelemetryClock(clock),
    override val telemetryService: TelemetryService = EmbraceTelemetryService(),
    override val spansSink: SpansSink = SpansSinkImpl(),
    override val openTelemetrySdk: OpenTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider
                    .builder()
                    .addSpanProcessor(EmbraceSpanProcessor(EmbraceSpanExporter(spansSink)))
                    .setClock(openTelemetryClock)
                    .build()
            )
            .build(),
    override val tracer: Tracer = openTelemetrySdk.getTracer(BuildConfig.LIBRARY_PACKAGE_NAME, BuildConfig.VERSION_NAME),
    override val currentSessionSpan: CurrentSessionSpan =
        CurrentSessionSpanImpl(
            clock = openTelemetryClock,
            telemetryService = telemetryService,
            spansSink = spansSink,
            tracer = tracer
        ),
    override val spansService: SpansService = EmbraceSpansService(
        spansSink = spansSink,
        currentSessionSpan = currentSessionSpan,
        tracer = tracer,
    ),
    override val embraceTracer: EmbraceTracer = EmbraceTracer(spansService)
) : InitModule
