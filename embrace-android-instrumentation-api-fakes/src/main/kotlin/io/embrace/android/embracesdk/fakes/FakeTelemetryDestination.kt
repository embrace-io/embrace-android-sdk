package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SessionStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeTelemetryDestination : TelemetryDestination {

    val logEvents: MutableList<FakeLogData> = mutableListOf()
    val addedEvents = mutableListOf<FakeSessionEvent>()
    val attributes = mutableMapOf<String, String>()
    val createdSpans: MutableList<FakeSpanToken> = mutableListOf()
    fun completedSpans(): List<FakeSpanToken> = createdSpans.filterNot(FakeSpanToken::isRecording)

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
            name = schemaType.fixedObjectName,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            errorCode = null,
            parent = null,
            type = schemaType.telemetryType,
            internal = true,
            private = false,
            initialAttrs = schemaType.attributes() + mapOf(schemaType.telemetryType.asPair()),
            events = mutableListOf(),
        )

        createdSpans.add(token)
        return token
    }

    override fun startSpanCapture(
        name: String,
        startTimeMs: Long,
        parent: SpanToken?,
        type: EmbType,
    ): SpanToken {
        val token = FakeSpanToken(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            errorCode = null,
            parent = parent,
            type = type,
            internal = true,
            private = false,
            initialAttrs = emptyMap(),
            events = mutableListOf(),
        )

        createdSpans.add(token)
        return token
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCodeAttribute?,
        parent: SpanToken?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
    ) {
        val token = FakeSpanToken(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            initialAttrs = attributes,
            events = events.toMutableList(),
        )
        createdSpans.add(token)
    }

    override fun <T : Any> startSessionStateCapture(state: SchemaType.State<T>): SessionStateToken<T> = FakeSessionStateToken()

    override var sessionUpdateAction: (() -> Unit)? = null

    override var currentStatesProvider: () -> Map<EmbraceAttributeKey, Any> = { emptyMap() }
}
