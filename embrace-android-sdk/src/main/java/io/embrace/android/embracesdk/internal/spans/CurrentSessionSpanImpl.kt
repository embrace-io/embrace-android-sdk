package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.SessionSpanWriter
import io.embrace.android.embracesdk.arch.SpanAttributeData
import io.embrace.android.embracesdk.arch.SpanEventData
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class CurrentSessionSpanImpl(
    private val clock: Clock,
    private val telemetryService: TelemetryService,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val tracerSupplier: Provider<Tracer>,
) : CurrentSessionSpan, SessionSpanWriter {

    /**
     * Number of traces created in the current session. This value will be reset when a new session is created.
     */
    private val traceCount = AtomicInteger(0)

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val sessionSpan: AtomicReference<EmbraceSpan?> = AtomicReference(null)

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        synchronized(sessionSpan) {
            sessionSpan.set(startSessionSpan(sdkInitStartTimeNanos))
        }
    }

    override fun initialized(): Boolean = sessionSpan.get() != null

    /**
     * Creating a new Span is only possible if the current session span is active, the parent has already been started, and the total
     * session trace limit has not been reached. Once this method returns true, a new span is assumed to have been created and will
     * be counted as such towards the limits, so make sure there's no case afterwards where a Span is not created.
     */
    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        // Check conditions where no span can be created
        val currentSession = sessionSpan.get() ?: return false
        if (!currentSession.isRecording || (parent != null && parent.spanId == null)) {
            return false
        }

        // If a span can be created, always let internal spans be to be created
        return if (internal) {
            return true
        } else if (traceCount.get() >= SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            // If we have already reached the maximum number of spans created for this session, don't allow another one
            false
        } else {
            synchronized(traceCount) {
                traceCount.getAndIncrement() < SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION
            }
        }
    }

    override fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData> {
        val endingSessionSpan = sessionSpan.get() ?: return emptyList()
        synchronized(sessionSpan) {
            // Right now, session spans don't survive native crashes and sudden process terminations,
            // so telemetry will not be recorded in those cases, for now.
            val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

            telemetryAttributes.forEach {
                endingSessionSpan.addAttribute(it.key, it.value)
            }

            if (appTerminationCause == null) {
                endingSessionSpan.stop()
                spanRepository.clearCompletedSpans()
                sessionSpan.set(startSessionSpan(clock.now()))
            } else {
                endingSessionSpan.addAttribute(
                    appTerminationCause.keyName(),
                    appTerminationCause.name
                )
                endingSessionSpan.stop()
            }
            return spanSink.flushSpans()
        }
    }

    override fun addEvent(event: SpanEventData): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.addEvent(event.spanName, event.spanStartTimeNanos, event.attributes)
    }

    override fun addAttribute(attribute: SpanAttributeData): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.addAttribute(attribute.key, attribute.value)
    }

    /**
     * This method should always be used when starting a new session span
     */
    private fun startSessionSpan(startTimeNanos: Long): EmbraceSpan {
        traceCount.set(0)

        val spanBuilder = createEmbraceSpanBuilder(
            tracer = tracerSupplier(),
            name = "session-span",
            type = EmbraceAttributes.Type.SESSION
        )
            .setNoParent()
            .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)

        return EmbraceSpanImpl(spanBuilder, sessionSpan = true).apply {
            start()
        }
    }
}
