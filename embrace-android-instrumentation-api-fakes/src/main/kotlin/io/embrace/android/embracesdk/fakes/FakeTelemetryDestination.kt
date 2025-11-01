package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeTelemetryDestination : TelemetryDestination {

    val logEvents: MutableList<FakeLogData> = mutableListOf()
    val addedEvents = mutableListOf<FakeSessionEvent>()
    val attributes = mutableMapOf<String, String>()
    val createdSpans: MutableList<FakeSpanToken> = mutableListOf()

    override fun addLog(
        schemaType: SchemaType,
        severity: LogSeverity,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionInfo: Boolean,
        timestampMs: Long?,
    ) {
        logEvents.add(FakeLogData(schemaType, severity, message))
    }

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        addedEvents.add(FakeSessionEvent(schemaType, startTimeMs))
        return true
    }

    override fun removeSessionEvents(type: EmbType) {
        addedEvents.removeAll { it.schemaType.telemetryType.key == type.key }
    }

    override fun addSessionAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun removeSessionAttribute(key: String) {
        attributes.remove(key)
    }

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        autoTerminate: Boolean,
    ): SpanToken {
        val token = FakeSpanToken(
            schemaType.fixedObjectName,
            startTimeMs,
            null,
            null,
            schemaType.telemetryType,
            schemaType.attributes() + mapOf(schemaType.telemetryType.asPair()),
        )

        createdSpans.add(token)
        return token
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: String?,
        type: EmbType,
        attributes: Map<String, String>,
    ) {
        val token = FakeSpanToken(
            name,
            startTimeMs,
            endTimeMs,
            errorCode,
            type,
            attributes,
        )
        createdSpans.add(token)
    }
}
