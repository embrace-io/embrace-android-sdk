package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Declares functions for capturing telemetry.
 */
interface TelemetryDestination {

    /**
     * Captures a log.
     */
    fun addLog(
        schemaType: SchemaType,
        severity: LogSeverity,
        message: String,
        isPrivate: Boolean = false,
        addCurrentSessionInfo: Boolean = true,
        timestampMs: Long? = null,
    )

    /**
     * Add a span event for the given [schemaType] to the session span. If [startTimeMs] is null, the
     * current time will be used. Returns true if the event was added, otherwise false.
     */
    fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean

    /**
     * Remove all span events with the given [EmbType].
     */
    fun removeSessionEvents(type: EmbType)

    /**
     * Add the given key-value pair as an Attribute to the session span
     */
    fun addSessionAttribute(key: String, value: String)

    /**
     * Remove the attribute with the given key
     */
    fun removeSessionAttribute(key: String)

    /**
     * Starts a new span with the given [schemaType] and [startTimeMs].
     */
    fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        autoTerminate: Boolean = false,
    ): SpanToken?

    /**
     * Starts a new span with the given [name] and [startTimeMs].
     */
    fun startSpanCapture(
        name: String,
        startTimeMs: Long,
        parent: SpanToken? = null,
        type: EmbType = EmbType.Performance.Default,
    ): SpanToken?

    /**
     * Start a recording the given [SchemaType.State] for the current session
     */
    fun <T : Any> startSessionStateCapture(state: SchemaType.State<T>): SessionStateToken<T>

    /**
     * Records a span that has already completed.
     */
    fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCodeAttribute? = null,
        parent: SpanToken? = null,
        type: EmbType = EmbType.Performance.Default,
        internal: Boolean = true,
        private: Boolean = false,
        attributes: Map<String, String> = emptyMap(),
        events: List<SpanEvent> = emptyList(),
    )

    /**
     * Notify the SDK that the current session has updated telemetry or metadata
     */
    var sessionUpdateAction: (() -> Unit)?

    /**
     * Function that returns a snapshot of all of the current values of registered instances of [StateDataSource] as a name-value pair
     */
    var currentStatesProvider: () -> Map<EmbraceAttributeKey, Any>
}
