package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.tracing.Tracer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class CurrentSessionPartSpanImpl(
    private val openTelemetryClock: Clock,
    private val telemetryService: TelemetryService,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val tracerSupplier: Provider<Tracer>,
    private val openTelemetrySupplier: Provider<OpenTelemetry>,
    private val embraceSpanFactorySupplier: Provider<EmbraceSpanFactory>,
) : CurrentSessionPartSpan {

    /**
     * Number of traces created in the current session. This value will be reset when a new session is created.
     */
    private val traceCount = AtomicInteger(0)
    private val internalTraceCount = AtomicInteger(0)
    private val initialized = AtomicBoolean(false)

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val sessionSpan: AtomicReference<EmbraceSdkSpan?> = AtomicReference(null)
    private val lastSessionSpan: AtomicReference<EmbraceSdkSpan?> = AtomicReference(null)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (!initialized.get()) {
            synchronized(sessionSpan) {
                if (!initialized.get()) {
                    sessionSpan.set(startSessionSpan(sdkInitStartTimeMs))
                    initialized.set(sessionSpan.get() != null)
                }
            }
        }
    }

    override fun initialized(): Boolean = initialized.get()

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
            checkTraceCount(internalTraceCount, MAX_INTERNAL_SPANS_PER_SESSION)
        } else {
            checkTraceCount(traceCount, MAX_NON_INTERNAL_SPANS_PER_SESSION)
        }
    }

    private fun checkTraceCount(counter: AtomicInteger, limit: Int): Boolean {
        return if (counter.get() >= limit) {
            // If we have already reached the maximum number of spans created for this session, don't allow another one
            telemetryService.trackAppliedLimit("span", AppliedLimitType.DROP)
            false
        } else {
            synchronized(counter) {
                counter.getAndIncrement() < limit
            }
        }
    }

    override fun getSessionId(): String {
        return sessionSpan.get()?.getSystemAttribute(SessionAttributes.SESSION_ID) ?: ""
    }

    override fun spanStopCallback(spanId: String) {
        val currentSessionPartSpan = sessionSpan.get()
        val spanToStop = spanRepository.getSpan(spanId)

        if (currentSessionPartSpan != spanToStop) {
            spanToStop?.spanContext?.let { spanToStopContext ->
                if (currentSessionPartSpan != null) {
                    currentSessionPartSpan.addSystemLink(spanToStopContext, LinkType.EndedIn)
                    if (spanToStop.hasEmbraceAttribute(EmbType.State)) {
                        currentSessionPartSpan.addSystemLink(spanToStopContext, LinkType.State)
                    }
                }
            }

            val sessionId = currentSessionPartSpan?.getSystemAttribute(SessionAttributes.SESSION_ID)
            if (sessionId != null) {
                currentSessionPartSpan.spanContext?.let { sessionSpanContext ->
                    spanToStop?.addSystemLink(
                        linkedSpanContext = sessionSpanContext,
                        type = LinkType.EndSession,
                        attributes = mapOf(SessionAttributes.SESSION_ID to sessionId)
                    )
                }
            }
        }
    }

    override fun readySession(): Boolean {
        if (sessionSpan.get() == null) {
            synchronized(sessionSpan) {
                if (sessionSpan.get() == null) {
                    sessionSpan.set(startSessionSpan(openTelemetryClock.now().nanosToMillis()))
                    return sessionSpanReady()
                }
            }
        }
        return sessionSpanReady()
    }

    override fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause?,
    ): List<EmbraceSpanData> {
        synchronized(sessionSpan) {
            val endingSessionSpan = sessionSpan.get()
            return if (endingSessionSpan != null && endingSessionSpan.isRecording) {
                // Right now, session spans don't survive native crashes and sudden process terminations,
                // so telemetry will not be recorded in those cases, for now.
                val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

                telemetryAttributes.forEach {
                    endingSessionSpan.addAttribute(it.key, it.value)
                }

                if (appTerminationCause == null) {
                    endingSessionSpan.stop()
                    lastSessionSpan.set(endingSessionSpan)
                    spanRepository.clearCompletedSpans()
                    val newSession = if (startNewSession) {
                        startSessionSpan(openTelemetryClock.now().nanosToMillis())
                    } else {
                        null
                    }
                    sessionSpan.set(newSession)
                } else {
                    val crashTime = openTelemetryClock.now().nanosToMillis()
                    spanRepository.failActiveSpans(crashTime)
                    endingSessionSpan.setSystemAttribute(
                        appTerminationCause.key,
                        appTerminationCause.value
                    )
                    endingSessionSpan.stop(errorCode = ErrorCode.FAILURE, endTimeMs = crashTime)
                }
                spanSink.flushSpans()
            } else {
                emptyList()
            }
        }
    }

    override fun current(): EmbraceSdkSpan? = sessionSpan.get()

    /**
     * This method should always be used when starting a new session span
     */
    private fun startSessionSpan(startTimeMs: Long): EmbraceSdkSpan {
        traceCount.set(0)
        internalTraceCount.set(0)

        return embraceSpanFactorySupplier().create(
            OtelSpanStartArgs(
                name = "session",
                type = EmbType.Ux.Session,
                internal = true,
                private = false,
                tracer = tracerSupplier(),
                openTelemetry = openTelemetrySupplier()
            )
        ).apply {
            start(startTimeMs = startTimeMs)
            setSystemAttribute(SessionAttributes.SESSION_ID, Uuid.getEmbUuid())
            val previousSessionSpan = lastSessionSpan.get()
            previousSessionSpan?.spanContext?.let {
                val prevSessionId = previousSessionSpan.getSystemAttribute(SessionAttributes.SESSION_ID) ?: ""
                addSystemLink(
                    linkedSpanContext = it,
                    type = LinkType.PreviousSession,
                    attributes = mapOf(SessionAttributes.SESSION_ID to prevSessionId)
                )
            }
        }
    }

    private fun sessionSpanReady() = sessionSpan.get()?.isRecording ?: false

    companion object {
        const val MAX_INTERNAL_SPANS_PER_SESSION: Int = 5000
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION: Int = 500
    }
}
