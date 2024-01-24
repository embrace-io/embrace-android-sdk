package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of the core logic for [SpansService]
 */
internal class SpansServiceImpl(
    sdkInitStartTimeNanos: Long,
    private val clock: Clock,
    private val telemetryService: TelemetryService
) : SpansService {
    private val sdkTracerProvider: SdkTracerProvider
        by lazy {
            SdkTracerProvider
                .builder()
                .addSpanProcessor(EmbraceSpanProcessor(EmbraceSpanExporter(this)))
                .setClock(clock)
                .build()
        }

    private val openTelemetry: OpenTelemetry
        by lazy {
            OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .build()
        }

    private val tracer: Tracer
        by lazy {
            openTelemetry.getTracer(BuildConfig.LIBRARY_PACKAGE_NAME, BuildConfig.VERSION_NAME)
        }

    /**
     * Number of traces created in the current session. This value should be reset when a new session is created.
     */
    private val currentSessionTraceCount = AtomicInteger(0)

    private val currentSessionChildSpansCount = mutableMapOf<String, Int>()

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val currentSessionSpan: AtomicReference<Span> = AtomicReference(startSessionSpan(sdkInitStartTimeNanos))

    /**
     * Spans that have finished, successfully or not, that will be sent with the next session or background activity payload. These
     * should be cached along with the other data in the payload.
     */
    private val completedSpans: MutableList<EmbraceSpanData> = mutableListOf()

    private val spansRepository = SpansRepository()

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? {
        return if (EmbraceSpanImpl.inputsValid(name) && validateAndUpdateContext(parent, internal)) {
            EmbraceSpanImpl(
                spanBuilder = createRootSpanBuilder(name = name, type = type, internal = internal),
                parent = parent,
                spansRepository = spansRepository
            )
        } else {
            null
        }
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        code: () -> T
    ): T {
        return if (EmbraceSpanImpl.inputsValid(name) && validateAndUpdateContext(parent, internal)) {
            createRootSpanBuilder(name = name, type = type, internal = internal).updateParent(parent).record(code)
        } else {
            code()
        }
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean {
        if (startTimeNanos > endTimeNanos) {
            return false
        }

        return if (EmbraceSpanImpl.inputsValid(name, events, attributes) && validateAndUpdateContext(parent, internal)) {
            val span = createRootSpanBuilder(name = name, type = type, internal = internal)
                .updateParent(parent)
                .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
                .startSpan()
                .setAllAttributes(Attributes.builder().fromMap(attributes).build())

            events.forEach { event ->
                if (EmbraceSpanEvent.inputsValid(event.name, event.attributes)) {
                    span.addEvent(
                        event.name,
                        Attributes.builder().fromMap(event.attributes).build(),
                        event.timestampNanos,
                        TimeUnit.NANOSECONDS
                    )
                }
            }

            span.endSpan(errorCode, endTimeNanos)
            true
        } else {
            false
        }
    }

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode {
        try {
            synchronized(completedSpans) {
                completedSpans += spans.map { EmbraceSpanData(spanData = it) }
            }
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedSpans(): List<EmbraceSpanData> {
        synchronized(completedSpans) {
            return completedSpans.toList()
        }
    }

    override fun flushSpans(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData> {
        synchronized(completedSpans) {
            // Right now, session spans don't survive native crashes and sudden process terminations,
            // so telemetry will not be recorded in those cases, for now.
            val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

            currentSessionSpan.get().setAllAttributes(Attributes.builder().fromMap(telemetryAttributes).build())

            if (appTerminationCause == null) {
                currentSessionSpan.get().endSpan()
                currentSessionSpan.set(startSessionSpan(clock.now()))
            } else {
                currentSessionSpan.get()?.let {
                    it.setAttribute(appTerminationCause.keyName(), appTerminationCause.name)
                    it.endSpan()
                }
            }

            val flushedSpans = completedSpans.toList()
            completedSpans.clear()
            return flushedSpans
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spansRepository.getSpan(spanId)

    /**
     * Creating a new Span is only possible if the current session span is active, the parent has already been started, and the total
     * session trace limit has not been reached. Once this method returns true, a new span is assumed to have been created and will
     * be counted as such towards the limits, so make sure there's no case afterwards where a Span is not created.
     */
    private fun validateAndUpdateContext(parent: EmbraceSpan?, internal: Boolean): Boolean {
        if (!currentSessionSpan.get().isRecording || (parent != null && parent.spanId == null)) {
            return false
        }

        if (!internal) {
            if (parent == null) {
                if (currentSessionTraceCount.get() < MAX_TRACE_COUNT_PER_SESSION) {
                    synchronized(currentSessionTraceCount) {
                        if (currentSessionTraceCount.get() < MAX_TRACE_COUNT_PER_SESSION) {
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
                } else if (currentSpanCount < MAX_SPAN_COUNT_PER_TRACE) {
                    synchronized(currentSessionChildSpansCount) {
                        val currentSpanCountAgain = currentSessionChildSpansCount[rootSpanId]
                        if (currentSpanCountAgain == null || currentSpanCountAgain < MAX_SPAN_COUNT_PER_TRACE) {
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
        return createEmbraceSpanBuilder(name = "session-span", type = EmbraceAttributes.Type.SESSION)
            .setNoParent()
            .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
            .startSpan()
    }

    private fun createRootSpanBuilder(name: String, type: EmbraceAttributes.Type, internal: Boolean): SpanBuilder =
        createEmbraceSpanBuilder(name = name, type = type, internal = internal).setNoParent()

    private fun createEmbraceSpanBuilder(name: String, type: EmbraceAttributes.Type, internal: Boolean = true): SpanBuilder =
        tracer.embraceSpanBuilder(name, internal).setType(type)

    companion object {
        const val MAX_TRACE_COUNT_PER_SESSION = 100
        const val MAX_SPAN_COUNT_PER_TRACE = 10
    }
}
