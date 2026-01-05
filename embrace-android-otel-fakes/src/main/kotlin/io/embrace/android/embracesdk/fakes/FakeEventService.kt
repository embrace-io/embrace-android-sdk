package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.otel.logs.EventService

class FakeEventService : EventService {
    val events: MutableList<EventData> = mutableListOf()

    override fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        embraceAttributes: Map<String, String>,
    ) {
        events.add(EventData(
            logTimeMs = logTimeMs,
            schemaType = schemaType,
            severity = severity,
            message = message,
            isPrivate = isPrivate,
            attributes = embraceAttributes
        ))
    }

    class EventData(
        val logTimeMs: Long,
        val schemaType: SchemaType,
        val severity: Severity,
        val message: String,
        val isPrivate: Boolean,
        val attributes: Map<String, String>,
    )
}
