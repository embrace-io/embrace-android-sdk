package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SessionPartStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.UuidSource

class FakeTelemetryDestination(
    private val uuidSource: UuidSource = TestUuidSource(),
) : TelemetryDestination {

    val logEvents: MutableList<FakeLogData> = mutableListOf()
    val addedEvents = mutableListOf<FakeSessionEvent>()
    val attributes = mutableMapOf<String, String>()
    val createdSpans: MutableList<FakeSpanToken> = mutableListOf()
    val createdStateTokens: MutableList<FakeSessionPartStateToken<*>> = mutableListOf()
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

    override fun addSessionPartEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        addedEvents.add(FakeSessionEvent(schemaType, startTimeMs))
        return true
    }

    override fun addSessionPartAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun removeSessionPartAttribute(key: String) {
        attributes.remove(key)
    }

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        name: String,
        parentSpanId: String?,
        autoTerminate: Boolean,
        private: Boolean,
    ): SpanToken {
        // Model the resolved parent as its own span token, named for the span id it represents, so the
        // resulting span can adopt it as a parent and derive its traceparent from it.
        val parent = parentSpanId?.let { spanId ->
            FakeSpanToken(
                name = spanId,
                startTimeMs = startTimeMs,
                endTimeMs = null,
                errorCode = null,
                parent = null,
                type = schemaType.telemetryType,
                internal = true,
                private = false,
                initialAttrs = emptyMap(),
                events = mutableListOf(),
                uuidSource = uuidSource,
            )
        }

        val token = FakeSpanToken(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = null,
            errorCode = null,
            parent = parent,
            type = schemaType.telemetryType,
            internal = true,
            private = false,
            initialAttrs = schemaType.attributes() + mapOf(schemaType.telemetryType.asPair()),
            events = mutableListOf(),
            uuidSource = uuidSource,
        )

        createdSpans.add(token)
        return token
    }

    override fun startSpanCapture(
        name: String,
        startTimeMs: Long,
        parent: SpanToken?,
        type: EmbType,
        private: Boolean,
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
            uuidSource = uuidSource,
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
            uuidSource = uuidSource,
        )
        createdSpans.add(token)
    }

    override fun <T : Any> startSessionPartStateCapture(state: SchemaType.State<T>): SessionPartStateToken<T> {
        val token = FakeSessionPartStateToken<T>()
        createdStateTokens.add(token)
        return token
    }

    override var sessionUpdateAction: (() -> Unit)? = null

    override var currentStatesProvider: () -> Map<String, Any> = { emptyMap() }
}
