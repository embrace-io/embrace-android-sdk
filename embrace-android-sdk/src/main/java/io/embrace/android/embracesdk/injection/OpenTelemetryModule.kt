package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.spans.SpansRepository
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetrySdk
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Module that instantiates various OpenTelemetry related components
 */
internal interface OpenTelemetryModule {

    /**
     * Configuration for the OpenTelemetry SDK
     */
    val openTelemetryConfiguration: OpenTelemetryConfiguration

    /**
     * Caches [EmbraceSpan] instances that are in progress or completed in the current session
     */
    val spansRepository: SpansRepository

    /**
     * Provides storage for completed spans that have not been sent off-device
     */
    val spansSink: SpansSink

    /**
     * An instance of the OpenTelemetry component obtained from the wrapped SDK to create spans
     */
    val tracer: Tracer

    /**
     * Component that manages and provides access to the current session span
     */
    val currentSessionSpan: CurrentSessionSpan

    /**
     * Service to record spans
     */
    val spansService: SpansService

    /**
     * Implementation of public tracing API
     */
    val embraceTracer: EmbraceTracer

    /**
     * Implementation of internal tracing API
     */
    val internalTracer: InternalTracer
}

internal class OpenTelemetryModuleImpl(
    private val initModule: InitModule
) : OpenTelemetryModule {
    override val spansRepository: SpansRepository by lazy {
        SpansRepository()
    }

    override val spansSink: SpansSink by lazy {
        SpansSinkImpl()
    }

    override val openTelemetryConfiguration: OpenTelemetryConfiguration by lazy {
        OpenTelemetryConfiguration(
            spansSink
        )
    }

    private val openTelemetrySdk: OpenTelemetrySdk by lazy {
        OpenTelemetrySdk(
            openTelemetryClock = initModule.openTelemetryClock,
            configuration = openTelemetryConfiguration
        )
    }

    override val tracer: Tracer by lazy {
        openTelemetrySdk.getOpenTelemetryTracer()
    }

    override val currentSessionSpan: CurrentSessionSpan by lazy {
        CurrentSessionSpanImpl(
            clock = initModule.openTelemetryClock,
            telemetryService = initModule.telemetryService,
            spansRepository = spansRepository,
            spansSink = spansSink,
            tracerSupplier = { tracer }
        )
    }

    override val spansService: SpansService by singleton {
        EmbraceSpansService(
            spansRepository = spansRepository,
            currentSessionSpan = currentSessionSpan,
            tracerSupplier = { tracer },
        )
    }

    override val embraceTracer: EmbraceTracer by singleton {
        EmbraceTracer(
            spansService = spansService
        )
    }

    override val internalTracer: InternalTracer by lazy {
        InternalTracer(
            clock = initModule.clock,
            spansRepository = spansRepository,
            embraceTracer = embraceTracer
        )
    }
}
