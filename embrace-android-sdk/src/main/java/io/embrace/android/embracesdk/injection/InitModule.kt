package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.SpansRepository
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetrySdk
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.api.trace.Tracer

/**
 * A module of components and services required at [EmbraceImpl] instantiation time, i.e. before the SDK evens starts
 */
internal interface InitModule {
    /**
     * Clock instance locked to the time of creation used by the SDK throughout its lifetime
     */
    val clock: io.embrace.android.embracesdk.internal.clock.Clock

    /**
     * Service to track usage of public APIs and other internal metrics
     */
    val telemetryService: TelemetryService

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
}

internal class InitModuleImpl(
    override val clock: io.embrace.android.embracesdk.internal.clock.Clock =
        NormalizedIntervalClock(systemClock = SystemClock()),
    openTelemetryClock: io.opentelemetry.sdk.common.Clock = OpenTelemetryClock(clock)
) : InitModule {

    override val telemetryService: TelemetryService by singleton {
        EmbraceTelemetryService()
    }

    override val spansRepository: SpansRepository by singleton {
        SpansRepository()
    }

    override val spansSink: SpansSink by singleton {
        SpansSinkImpl()
    }

    private val openTelemetrySdk: OpenTelemetrySdk by singleton {
        OpenTelemetrySdk(
            openTelemetryClock = openTelemetryClock,
            spanProcessor = EmbraceSpanProcessor(EmbraceSpanExporter(spansSink))
        )
    }

    override val tracer: Tracer by singleton {
        openTelemetrySdk.getOpenTelemetryTracer()
    }

    override val currentSessionSpan: CurrentSessionSpan by singleton {
        CurrentSessionSpanImpl(
            clock = openTelemetryClock,
            telemetryService = telemetryService,
            spansRepository = spansRepository,
            spansSink = spansSink,
            tracer = tracer
        )
    }

    override val spansService: SpansService by singleton {
        EmbraceSpansService(
            spansRepository = spansRepository,
            currentSessionSpan = currentSessionSpan,
            tracer = tracer,
        )
    }

    override val embraceTracer: EmbraceTracer by singleton {
        EmbraceTracer(
            spansRepository = spansRepository,
            spansService = spansService
        )
    }
}
