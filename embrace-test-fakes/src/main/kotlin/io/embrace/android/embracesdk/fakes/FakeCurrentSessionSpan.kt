package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.toEmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.sdk.trace.data.StatusData
import java.util.concurrent.atomic.AtomicInteger

public class FakeCurrentSessionSpan(
    private val clock: FakeClock = FakeClock()
) : CurrentSessionSpan {
    public var initializedCallCount: Int = 0
    public var addedEvents: MutableList<SpanEventData> = mutableListOf()
    public var addedAttributes: MutableList<SpanAttributeData> = mutableListOf()
    public var sessionSpan: FakeSpanData? = null

    private val sessionIteration = AtomicInteger(1)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sessionSpan = newSessionSpan(sdkInitStartTimeMs)
    }

    override fun addEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        addedEvents.add(SpanEventData(schemaType, startTimeMs))
        return true
    }

    override fun removeEvents(type: EmbType) {
        addedEvents.removeAll { it.schemaType.telemetryType.key == type.key }
    }

    override fun addCustomAttribute(attribute: SpanAttributeData): Boolean = addedAttributes.add(attribute)

    override fun removeCustomAttribute(key: String): Boolean {
        val attributeToRemove = addedAttributes.find { it.key == key } ?: return false
        return addedAttributes.remove(attributeToRemove)
    }

    override fun initialized(): Boolean {
        initializedCallCount++
        return true
    }

    override fun readySession(): Boolean {
        sessionSpan = newSessionSpan(clock.now())
        return true
    }

    override fun endSession(startNewSession: Boolean, appTerminationCause: AppTerminationCause?): List<EmbraceSpanData> {
        val endingSessionSpan = checkNotNull(sessionSpan)
        endingSessionSpan.endTimeNanos = clock.nowInNanos()
        endingSessionSpan.spanStatus = if (appTerminationCause == null) StatusData.ok() else StatusData.error()
        sessionIteration.incrementAndGet()
        sessionSpan = if (appTerminationCause == null) newSessionSpan(clock.now()) else null
        return listOf((endingSessionSpan).toEmbraceSpanData())
    }

    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        return true
    }

    override fun getSessionId(): String {
        return "testSessionId$sessionIteration"
    }

    public fun getAttribute(key: String): String? = addedAttributes.lastOrNull { it.key == key }?.value

    public fun attributeCount(): Int = addedAttributes.size

    private fun newSessionSpan(startTimeMs: Long) =
        FakeSpanData(
            startEpochNanos = startTimeMs.millisToNanos(),
            name = "fake-session-span${getSessionId()}"
        )
}
