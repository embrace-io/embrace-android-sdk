package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.SpanAttributeData
import io.embrace.android.embracesdk.arch.SpanEventData
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class FakeCurrentSessionSpan : CurrentSessionSpan {

    var initializedCallCount = 0

    override fun initializeService(sdkInitStartTimeNanos: Long) {
    }

    override fun addEvent(event: SpanEventData): Boolean {
        return true
    }

    override fun addAttribute(attribute: SpanAttributeData): Boolean {
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
