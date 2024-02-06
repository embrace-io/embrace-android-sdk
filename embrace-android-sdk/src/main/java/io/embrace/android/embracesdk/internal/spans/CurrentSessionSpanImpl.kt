package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.embrace.android.embracesdk.utils.lockAndRun
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class CurrentSessionSpanImpl(
    private val clock: Clock,
    private val telemetryService: TelemetryService,
    private val spansRepository: SpansRepository,
    private val spansSink: SpansSink,
    private val tracer: Tracer,
) : CurrentSessionSpan {
    /**
     * Number of traces created in the current session. This value will be reset when a new session is created.
     */
    private val traceCount = AtomicInteger(0)

    /**
     * The total child spans per trace. This should not be cleared at session boundaries because child spans can be created for
     * completed spans.
     */
    private val traceChildSpanCount = ConcurrentHashMap<String, Int>()

    /**
     * Provide locks for each trace so creating spans in unrelated traces will not block each other
     */
    private val mutatingTraces: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val sessionSpan: AtomicReference<Span?> = AtomicReference(null)

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
        val currentSession = sessionSpan.get() ?: return false
        if (!currentSession.isRecording || (parent != null && parent.spanId == null)) {
            return false
        }

        if (internal) {
            return true
        }

        if (parent == null) {
            return if (traceCount.get() >= SpansServiceImpl.MAX_TRACE_COUNT_PER_SESSION) {
                false
            } else {
                synchronized(traceCount) {
                    traceCount.getAndIncrement() < SpansServiceImpl.MAX_TRACE_COUNT_PER_SESSION
                }
            }
        }

        // Recursively traverse the current span's parent to obtain the spanId of the root of the trace
        var currentSpan: EmbraceSpan = parent
        while (currentSpan.parent != null) {
            currentSpan.parent?.let { currentSpan = it }
        }

        val rootSpanId = currentSpan.spanId ?: return false
        return mutatingTraces.lockAndRun(rootSpanId) {
            val childSpanCount = traceChildSpanCount[rootSpanId] ?: 0
            if (childSpanCount >= SpansServiceImpl.MAX_SPAN_COUNT_PER_TRACE) {
                return@lockAndRun false
            }

            if (childSpanCount == 0) {
                // The first time we encounter a particular rootSpanId is when a child is being added to it.
                // Prior to that, when adding a prospective root span, the ID is not known yet. So the first time a root span
                // is encountered add both it and the new child to the count.
                //
                // NOTE: Because we don't know whether the root span is internal or not at this point, it is assumed that it isn't.
                // Therefore, it will count towards the limit if a non-internal span is added to the trace.
                traceChildSpanCount[rootSpanId] = 2
            } else {
                traceChildSpanCount[rootSpanId] = childSpanCount + 1
            }

            return@lockAndRun true
        }
    }

    override fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData> {
        val endingSessionSpan = sessionSpan.get() ?: return emptyList()
        synchronized(sessionSpan) {
            // Right now, session spans don't survive native crashes and sudden process terminations,
            // so telemetry will not be recorded in those cases, for now.
            val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()
            endingSessionSpan.setAllAttributes(Attributes.builder().fromMap(telemetryAttributes).build())

            if (appTerminationCause == null) {
                endingSessionSpan.endSpan()
                spansRepository.clearCompletedSpans()
                sessionSpan.set(startSessionSpan(clock.now()))
            } else {
                endingSessionSpan.setAttribute(appTerminationCause.keyName(), appTerminationCause.name)
                endingSessionSpan.endSpan()
            }
            return spansSink.flushSpans()
        }
    }

    /**
     * This method should always be used when starting a new session span
     */
    private fun startSessionSpan(startTimeNanos: Long): Span {
        traceCount.set(0)
        return createEmbraceSpanBuilder(tracer = tracer, name = "session-span", type = EmbraceAttributes.Type.SESSION)
            .setNoParent()
            .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
            .startSpan()
    }
}
