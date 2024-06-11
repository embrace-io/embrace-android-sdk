package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * The span that models the lifetime of the current session or background activity
     */
    private val sessionSpan: AtomicReference<PersistableEmbraceSpan?> = AtomicReference(null)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (sessionSpan.get() == null) {
            synchronized(sessionSpan) {
                if (sessionSpan.get() == null) {
                    sessionSpan.set(startSessionSpan(sdkInitStartTimeMs))
                }
            }
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

    override fun getSessionId(): String {
        return sessionSpan.get()?.getSystemAttribute(embSessionId) ?: ""
    }

    override fun endSession(appTerminationCause: AppTerminationCause?): List<EmbraceSpanData> {
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
                    spanRepository.clearCompletedSpans()
                    sessionSpan.set(startSessionSpan(openTelemetryClock.now().nanosToMillis()))
                } else {
                    val crashTime = openTelemetryClock.now().nanosToMillis()
                    spanRepository.failActiveSpans(crashTime)
                    endingSessionSpan.setSystemAttribute(appTerminationCause.key, appTerminationCause.value)
                    endingSessionSpan.stop(errorCode = ErrorCode.FAILURE, endTimeMs = crashTime)
                }
                spanSink.flushSpans()
            } else {
                emptyList()
            }
        }
    }

    override fun <T> addEvent(obj: T, mapper: T.() -> SpanEventData): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        val event = obj.mapper()
        return currentSession.addEvent(
            event.schemaType.fixedObjectName.toEmbraceObjectName(),
            event.spanStartTimeMs,
            event.schemaType.attributes() + event.schemaType.telemetryType.toEmbraceKeyValuePair()
        )
    }

    override fun addEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.addEvent(
            schemaType.fixedObjectName.toEmbraceObjectName(),
            startTimeMs,
            schemaType.attributes() + schemaType.telemetryType.toEmbraceKeyValuePair()
        )
    }

    override fun removeEvents(type: EmbType) {
        val currentSession = sessionSpan.get() ?: return
        currentSession.removeEvents(type)
    }

    override fun addCustomAttribute(attribute: SpanAttributeData): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.addAttribute(attribute.key, attribute.value)
    }

    override fun removeCustomAttribute(key: String): Boolean {
        val currentSession = sessionSpan.get() ?: return false
        return currentSession.removeCustomAttribute(key)
    }

    /**
     * This method should always be used when starting a new session span
     */
    private fun startSessionSpan(startTimeMs: Long): PersistableEmbraceSpan {
        traceCount.set(0)

        return embraceSpanFactorySupplier().create(
            name = "session",
            type = EmbType.Ux.Session,
            internal = true,
            private = false
        ).apply {
            start(startTimeMs = startTimeMs)
            setSystemAttribute(embSessionId, Uuid.getEmbUuid())
        }
    }
}
