package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class FakeCurrentSessionSpan : CurrentSessionSpan {

    var initializedCallCount = 0
    var addedEvents = mutableListOf<SpanEventData>()
    var addedAttributes = mutableListOf<SpanAttributeData>()

    override fun initializeService(sdkInitStartTimeMs: Long) {
    }

    override fun <T> addEvent(obj: T, mapper: T.() -> SpanEventData): Boolean {
        addedEvents.add(obj.mapper())
        return true
    }

    override fun addAttribute(attribute: SpanAttributeData): Boolean {
        addedAttributes.add(attribute)
        return true
    }

    override fun initialized(): Boolean {
        initializedCallCount++
        return true
    }

    override fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData> {
        return emptyList()
    }

    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        return true
    }
}
