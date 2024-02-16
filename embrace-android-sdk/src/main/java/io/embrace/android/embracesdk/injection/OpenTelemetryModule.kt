package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetrySdk
import io.opentelemetry.api.trace.Tracer

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
    val spanRepository: SpanRepository

    /**
     * Provides storage for completed spans that have not been sent off-device
     */
    val spanSink: SpanSink

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
    val spanService: SpanService

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

    override val spanRepository: SpanRepository by lazy {
        SpanRepository()
    }

    override val spanSink: SpanSink by lazy {
        SpanSinkImpl()
    }

    override val openTelemetryConfiguration: OpenTelemetryConfiguration by lazy {
        OpenTelemetryConfiguration(
            spanSink
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
            spanRepository = spanRepository,
            spanSink = spanSink,
            tracerSupplier = { tracer }
        )
    }

    override val spanService: SpanService by singleton {
        EmbraceSpanService(
            spanRepository = spanRepository,
            currentSessionSpan = currentSessionSpan,
            tracerSupplier = { tracer },
        )
    }

    override val embraceTracer: EmbraceTracer by singleton {
        EmbraceTracer(
            clock = initModule.clock,
            spanService = spanService
        )
    }

    override val internalTracer: InternalTracer by lazy {
        InternalTracer(
            spanRepository = spanRepository,
            embraceTracer = embraceTracer
        )
    }
}
