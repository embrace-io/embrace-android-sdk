package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class CurrentSessionSpanImpl(
    private val clock: Clock,
    private val telemetryService: TelemetryService,
    private val spansSink: SpansSink,
    private val tracer: Tracer,
) : CurrentSessionSpan {
    /**
     * Number of traces created in the current session. This value should be reset when a new session is created.
     */
    private val currentSessionTraceCount = AtomicInteger(0)

    private val currentSessionChildSpansCount = mutableMapOf<String, Int>()

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val currentSessionSpan: AtomicReference<Span> = AtomicReference()

    override fun startInitialSession(sdkInitStartTimeNanos: Long) {
        synchronized(currentSessionSpan) {
            currentSessionSpan.set(startSessionSpan(sdkInitStartTimeNanos))
        }
    }

    /**
     * Creating a new Span is only possible if the current session span is active, the parent has already been started, and the total
     * session trace limit has not been reached. Once this method returns true, a new span is assumed to have been created and will
     * be counted as such towards the limits, so make sure there's no case afterwards where a Span is not created.
     */
    override fun validateAndUpdateContext(parent: EmbraceSpan?, internal: Boolean): Boolean {
        if (!currentSessionSpan.get().isRecording || (parent != null && parent.spanId == null)) {
            return false
        }

        if (!internal) {
            if (parent == null) {
                if (currentSessionTraceCount.get() < SpansServiceImpl.MAX_TRACE_COUNT_PER_SESSION) {
                    synchronized(currentSessionTraceCount) {
                        if (currentSessionTraceCount.get() < SpansServiceImpl.MAX_TRACE_COUNT_PER_SESSION) {
                            currentSessionTraceCount.incrementAndGet()
                        } else {
                            return false
                        }
                    }
                } else {
                    return false
                }
            } else {
                val rootSpanId = getRootSpanId(parent)
                val currentSpanCount = currentSessionChildSpansCount[rootSpanId]
                if (currentSpanCount == null) {
                    updateChildrenCount(rootSpanId)
                } else if (currentSpanCount < SpansServiceImpl.MAX_SPAN_COUNT_PER_TRACE) {
                    synchronized(currentSessionChildSpansCount) {
                        val currentSpanCountAgain = currentSessionChildSpansCount[rootSpanId]
                        if (currentSpanCountAgain == null || currentSpanCountAgain < SpansServiceImpl.MAX_SPAN_COUNT_PER_TRACE) {
                            updateChildrenCount(rootSpanId)
                        } else {
                            return false
                        }
                    }
                } else {
                    return false
                }
            }
        }

        return true
    }

    override fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData> {
        synchronized(this) {
            // Right now, session spans don't survive native crashes and sudden process terminations,
            // so telemetry will not be recorded in those cases, for now.
            val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

            currentSessionSpan.get().setAllAttributes(Attributes.builder().fromMap(telemetryAttributes).build())

            if (appTerminationCause == null) {
                currentSessionSpan.get().endSpan()
                spansSink.getSpansRepository()?.clearCompletedSpans()
                currentSessionSpan.set(startSessionSpan(clock.now()))
            } else {
                currentSessionSpan.get()?.let {
                    it.setAttribute(appTerminationCause.keyName(), appTerminationCause.name)
                    it.endSpan()
                }
            }

            return spansSink.flushSpans()
        }
    }

    override fun completedSpans(): List<EmbraceSpanData> = spansSink.completedSpans()

    private fun getRootSpanId(span: EmbraceSpan): String {
        var currentSpan: EmbraceSpan = span
        while (currentSpan.parent != null) {
            currentSpan.parent?.let { currentSpan = it }
        }

        return currentSpan.spanId ?: ""
    }

    private fun updateChildrenCount(rootSpanId: String) {
        val currentCount = currentSessionChildSpansCount[rootSpanId]
        if (currentCount == null) {
            // The first time we'll know a root span ID is when a child is being added to it. Prior to that, when adding a prospective
            // root span, the ID is not known yet. So the first time a root span is encountered add both it and the new child to the count.
            //
            // NOTE: Because we don't know whether the root span is internal or not at this point, it is assumed that it isn't.
            // Therefore, it will count towards the limit if a non-internal span is added to the trace.
            currentSessionChildSpansCount[rootSpanId] = 2
        } else {
            currentSessionChildSpansCount[rootSpanId] = currentCount + 1
        }
    }

    /**
     * This method should always be used when starting a new session span
     */
    private fun startSessionSpan(startTimeNanos: Long): Span {
        currentSessionTraceCount.set(0)
        return createEmbraceSpanBuilder(tracer = tracer, name = "session-span", type = EmbraceAttributes.Type.SESSION)
            .setNoParent()
            .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
            .startSpan()
    }
}
