package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeSessionSpanWriter : SessionSpanWriter {
    val addedEvents = mutableListOf<FakeSessionEvent>()
    val attributes = mutableMapOf<String, String>()

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        addedEvents.add(FakeSessionEvent(schemaType, startTimeMs))
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
