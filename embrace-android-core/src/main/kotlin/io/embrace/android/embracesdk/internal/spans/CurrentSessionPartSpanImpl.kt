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
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.tracing.Tracer
import java.util.concurrent.atomic.AtomicInteger

internal class CurrentSessionPartSpanImpl(
    private val openTelemetryClock: Clock,
    private val telemetryService: TelemetryService,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val tracerSupplier: Provider<Tracer>,
    private val openTelemetrySupplier: Provider<OpenTelemetry>,
    private val embraceSpanFactorySupplier: Provider<EmbraceSpanFactory>,
    private val uuidSource: UuidSource,
) : CurrentSessionPartSpan {

    /**
     * Guards initialization and starting of new sessions. This is not used to fully guard the sessionState which is read during
     * [canStartNewSpan] and [spanStopCallback] without guards to avoid creating a bottleneck.
     */
    private val sessionTransitionLock = Any()

    @Volatile
    private var initialized: Boolean = false

    /**
     * Encapsulation of the current session span (if there is one) and its trace counts.
     */
    @Volatile
    private var sessionPartState: SessionPartState? = null

    @Volatile
    private var lastSessionPartSpan: EmbraceSdkSpan? = null

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (!initialized) {
            synchronized(sessionTransitionLock) {
                if (!initialized) {
                    ensureInitialSessionPartExists { sdkInitStartTimeMs }
                    initialized = sessionPartState != null
                }
            }
        }
    }

    override fun initialized(): Boolean = initialized

    /**
     * Creating a new Span is only possible if the current session span is active, the parent has already been started, and the total
     * session trace limit has not been reached. Once this method returns true, a new span is assumed to have been created and will
     * be counted as such towards the limits, so make sure there's no case afterwards where a Span is not created.
     */
    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        val state = sessionPartState ?: return false
        if (!state.isReady || (parent != null && parent.spanId == null)) {
            return false
        }

        return if (internal) {
            checkTraceCount(state.internalTraceCount, MAX_INTERNAL_SPANS_PER_SESSION)
        } else {
            checkTraceCount(state.traceCount, MAX_NON_INTERNAL_SPANS_PER_SESSION)
        }
    }

    private fun checkTraceCount(counter: AtomicInteger, limit: Int): Boolean {
        return if (counter.get() >= limit) {
            telemetryService.trackAppliedLimit("span", AppliedLimitType.DROP)
            false
        } else {
            counter.getAndIncrement() < limit
        }
    }

    override fun getId(): String = sessionPartState?.sessionPartId ?: ""

    override fun spanStopCallback(spanId: String) {
        val currentSessionPartSpan = sessionPartState?.span
        val spanToStop = spanRepository.getSpan(spanId)

        if (currentSessionPartSpan != spanToStop) {
            val linkAttrs = currentSessionPartSpan?.partLinkAttrs() ?: emptyMap()
            spanToStop?.spanContext?.let { spanToStopContext ->
                if (currentSessionPartSpan != null) {
                    currentSessionPartSpan.addSystemLink(
                        linkedSpanContext = spanToStopContext,
                        type = LinkType.EndedIn,
                        attributes = linkAttrs
                    )
                    if (spanToStop.hasEmbraceAttribute(EmbType.State)) {
                        currentSessionPartSpan.addSystemLink(
                            linkedSpanContext = spanToStopContext,
                            type = LinkType.State,
                            attributes = linkAttrs
                        )
                    }
                }
            }

            currentSessionPartSpan?.spanContext?.let { sessionPartSpanContext ->
                spanToStop?.addSystemLink(
                    linkedSpanContext = sessionPartSpanContext,
                    type = LinkType.EndSession,
                    attributes = linkAttrs
                )
            }
        }
    }

    override fun readySession(): Boolean {
        ensureInitialSessionPartExists { openTelemetryClock.now().nanosToMillis() }
        return sessionPartSpanReady()
    }

    override fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause?,
    ): List<EmbraceSpanData> {
        synchronized(sessionTransitionLock) {
            val endingSessionPartSpan = sessionPartState?.span
            return if (endingSessionPartSpan != null && endingSessionPartSpan.isRecording) {
                // Right now, session spans don't survive native crashes and sudden process terminations,
                // so telemetry will not be recorded in those cases, for now.
                val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

                telemetryAttributes.forEach {
                    endingSessionPartSpan.addAttribute(it.key, it.value)
                }

                if (appTerminationCause == null) {
                    endingSessionPartSpan.stop()
                    lastSessionPartSpan = endingSessionPartSpan
                    spanRepository.clearCompletedSpans()
                    sessionPartState = if (startNewSession) {
                        startSessionPartSpan(openTelemetryClock.now().nanosToMillis())
                    } else {
                        null
                    }
                } else {
                    val crashTime = openTelemetryClock.now().nanosToMillis()
                    spanRepository.failActiveSpans(crashTime)
                    endingSessionPartSpan.setSystemAttribute(
                        appTerminationCause.key,
                        appTerminationCause.value
                    )
                    endingSessionPartSpan.stop(errorCode = ErrorCode.FAILURE, endTimeMs = crashTime)
                }
                spanSink.flushSpans()
            } else {
                emptyList()
            }
        }
    }

    override fun current(): EmbraceSdkSpan? = sessionPartState?.span

    /**
     * Creates the current session part span if one does not already exist.
     */
    private inline fun ensureInitialSessionPartExists(startTimeMs: () -> Long) {
        if (sessionPartState == null) {
            synchronized(sessionTransitionLock) {
                if (sessionPartState == null) {
                    sessionPartState = startSessionPartSpan(startTimeMs())
                }
            }
        }
    }

    /**
     * This method should always be used when starting a new session part span. It creates a UUID for the session part and
     * puts it in the span. This is the one true place for creating a new session part and its persisted metadata.
     */
    private fun startSessionPartSpan(startTimeMs: Long): SessionPartState {
        val sessionPartId = uuidSource.createUuid()
        val span = embraceSpanFactorySupplier().create(
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
            setSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionPartId)
            val previousSessionPartSpan = lastSessionPartSpan
            previousSessionPartSpan?.spanContext?.let {
                addSystemLink(
                    linkedSpanContext = it,
                    type = LinkType.PreviousSession,
                    attributes = previousSessionPartSpan.partLinkAttrs()
                )
            }
        }
        return SessionPartState(span, sessionPartId)
    }

    private fun sessionPartSpanReady() = sessionPartState?.isReady == true

    /**
     * Encapsulates the current session span and the current trace counts for limit enforcement.
     */
    private class SessionPartState(val span: EmbraceSdkSpan, val sessionPartId: String) {
        val traceCount: AtomicInteger = AtomicInteger(0)
        val internalTraceCount: AtomicInteger = AtomicInteger(0)

        val isReady: Boolean get() = span.isRecording
    }

    /**
     * Attributes for a span link that references the session part represented by this span.
     */
    private fun EmbraceSdkSpan.partLinkAttrs(): Map<String, String> = buildMap {
        getSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID)?.let {
            put(EmbSessionAttributes.EMB_SESSION_PART_ID, it)
        }
        getSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID)?.let {
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, it)
            put(SessionAttributes.SESSION_ID, it)
        }
    }

    companion object {
        const val MAX_INTERNAL_SPANS_PER_SESSION: Int = 5000
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION: Int = 500
    }
}
