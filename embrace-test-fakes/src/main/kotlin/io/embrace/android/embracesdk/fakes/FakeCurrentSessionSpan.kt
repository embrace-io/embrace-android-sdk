package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.atomic.AtomicInteger

class FakeCurrentSessionSpan(
    private val clock: FakeClock = FakeClock(),
) : CurrentSessionSpan {
    val addedEvents = mutableListOf<SpanEventData>()
    val attributes = mutableMapOf<String, String>()
    val stoppedSpans = mutableSetOf<String>()
    var initializedCallCount: Int = 0
    var sessionSpan: FakeEmbraceSdkSpan? = null

    private val sessionIteration = AtomicInteger(1)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sessionSpan = newSessionSpan(sdkInitStartTimeMs)
    }

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        addedEvents.add(SpanEventData(schemaType, startTimeMs))
        return true
    }

    override fun removeSessionEvents(type: EmbType) {
        addedEvents.removeAll { it.schemaType.telemetryType.key == type.key }
    }

    override fun addSessionAttribute(attribute: SpanAttributeData) {
        attributes[attribute.key] = attribute.value
    }

    override fun removeSessionAttribute(key: String) {
        attributes.remove(key)
    }

    override fun initialized(): Boolean {
        initializedCallCount++
        return true
    }

    override fun readySession(): Boolean {
        sessionSpan = newSessionSpan(clock.now())
        return true
    }

    override fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause?,
    ): List<EmbraceSpanData> {
        val endingSessionSpan = checkNotNull(sessionSpan)
        val errorCode = if (appTerminationCause != null) {
            ErrorCode.FAILURE
        } else {
            null
        }
        endingSessionSpan.stop(errorCode, clock.now())
        val payload = listOf(checkNotNull(endingSessionSpan.snapshot()).toEmbracePayload())
        sessionIteration.incrementAndGet()
        sessionSpan = if (appTerminationCause == null) newSessionSpan(clock.now()) else null
        return payload
    }

    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        return true
    }

    override fun getSessionId(): String {
        return "testSessionId$sessionIteration"
    }

    override fun spanStopCallback(spanId: String) {
        stoppedSpans.add(spanId)
    }

    fun getAttribute(key: String): String? = attributes[key]

    fun attributeCount(): Int = attributes.size

    private fun newSessionSpan(startTimeMs: Long) =
        FakeEmbraceSdkSpan.sessionSpan(
            sessionId = "fake-session-span-id",
            startTimeMs = startTimeMs,
            lastHeartbeatTimeMs = startTimeMs
        )
}
