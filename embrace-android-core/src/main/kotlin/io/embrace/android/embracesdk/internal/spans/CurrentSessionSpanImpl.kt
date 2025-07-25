package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class)
internal class CurrentSessionSpanImpl(
    private val openTelemetryClock: Clock,
    private val telemetryService: TelemetryService,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val embraceSpanFactorySupplier: Provider<EmbraceSpanFactory>,
) : CurrentSessionSpan, SessionSpanWriter {

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
            false
        } else {
            synchronized(counter) {
                counter.getAndIncrement() < limit
            }
        }
    }

    override fun getSessionId(): String {
        return sessionSpan.get()?.getSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key) ?: ""
    }

    @OptIn(ExperimentalApi::class)
    override fun spanStopCallback(spanId: String) {
        val currentSessionSpan = sessionSpan.get()
        val spanToStop = spanRepository.getSpan(spanId)

        if (currentSessionSpan != spanToStop) {
            spanToStop?.spanContext?.let { spanToStopContext ->
                currentSessionSpan?.addSystemLink(spanToStopContext.toOtelKotlin(), LinkType.EndedIn)
            }

            val sessionId = currentSessionSpan?.getSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key)
            if (sessionId != null) {
                currentSessionSpan.spanContext?.let { sessionSpanContext ->
                    spanToStop?.addSystemLink(
                        linkedSpanContext = sessionSpanContext.toOtelKotlin(),
                        type = LinkType.EndSession,
                        attributes = mapOf(SessionIncubatingAttributes.SESSION_ID.key to sessionId)
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
                        appTerminationCause.key.name,
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

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.addSystemEvent(
            schemaType.fixedObjectName.toEmbraceObjectName(),
            startTimeMs,
            schemaType.attributes() + schemaType.telemetryType.asPair()
        )
    }

    override fun removeSessionEvents(type: EmbType) {
        val currentSession = sessionSpan.get() ?: return
        currentSession.removeSystemEvents(type)
    }

    override fun addSessionAttribute(attribute: SpanAttributeData) {
        val currentSession = sessionSpan.get() ?: return
        currentSession.addSystemAttribute(attribute.key, attribute.value)
    }

    override fun removeSessionAttribute(key: String) {
        val currentSession = sessionSpan.get() ?: return
        currentSession.removeSystemAttribute(key)
    }

    /**
     * This method should always be used when starting a new session span
     */
    @OptIn(ExperimentalApi::class)
    private fun startSessionSpan(startTimeMs: Long): EmbraceSdkSpan {
        traceCount.set(0)
        internalTraceCount.set(0)

        return embraceSpanFactorySupplier().create(
            name = "session",
            type = EmbType.Ux.Session,
            internal = true,
            private = false,
        ).apply {
            start(startTimeMs = startTimeMs)
            setSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key, Uuid.getEmbUuid())
            val previousSessionSpan = lastSessionSpan.get()
            previousSessionSpan?.spanContext?.let {
                val prevSessionId = previousSessionSpan.getSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key) ?: ""
                addSystemLink(
                    linkedSpanContext = it.toOtelKotlin(),
                    type = LinkType.PreviousSession,
                    attributes = mapOf(SessionIncubatingAttributes.SESSION_ID.key to prevSessionId)
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
