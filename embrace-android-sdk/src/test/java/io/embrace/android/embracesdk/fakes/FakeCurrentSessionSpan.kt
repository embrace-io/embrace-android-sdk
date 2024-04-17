package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class FakeCurrentSessionSpan : CurrentSessionSpan {

    var initializedCallCount = 0
    var addedEvents = mutableListOf<SpanEventData>()
    var spanData = listOf<EmbraceSpanData>()
    private var addedAttributes = mutableListOf<SpanAttributeData>()

    override fun initializeService(sdkInitStartTimeMs: Long) {
    }

    override fun <T> addEvent(obj: T, mapper: T.() -> SpanEventData): Boolean {
        addedEvents.add(obj.mapper())
        return true
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

    override fun endSession(appTerminationCause: AppTerminationCause?): List<EmbraceSpanData> {
        return spanData
    }

    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        return true
    }

    override fun getSessionId(): String {
        return "testSessionId"
    }

    fun getAttribute(key: String): String? = addedAttributes.lastOrNull { it.key == key }?.value

    fun attributeCount(): Int = addedAttributes.size
}
