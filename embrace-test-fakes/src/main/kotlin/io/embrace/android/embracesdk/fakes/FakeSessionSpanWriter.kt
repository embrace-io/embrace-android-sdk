package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeSessionSpanWriter : SessionSpanWriter {
    val addedEvents = mutableListOf<SpanEventData>()
    val attributes = mutableMapOf<String, String>()
    var sessionSpan: FakeEmbraceSdkSpan? = null

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
}
