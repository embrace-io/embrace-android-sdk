package io.embrace.android.embracesdk.internal.otel.state

/**
 * The names telemetry is associated with state under.
 */
object StateAttributes {

    /**
     * The number of the last state change before a span started, before a log was emitted, or that an event describes.
     */
    const val SEQUENCE: String = "emb.state.seq"

    /**
     * The number of the last state change before a span ended, where that is not [SEQUENCE].
     */
    const val END_SEQUENCE: String = "emb.state.end_seq"

    /**
     * Followed by a state's name: its value.
     */
    const val VALUE_PREFIX: String = "emb.state.value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value.
     */
    const val VALUE_TYPE_PREFIX: String = "emb.state.value_type."

    /**
     * Followed by a state's name: its value when a span ended, where that is not its value when the span started.
     */
    const val END_VALUE_PREFIX: String = "emb.state.end_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a span ended.
     */
    const val END_VALUE_TYPE_PREFIX: String = "emb.state.end_value_type."

    /**
     * The name of the state a change event is about.
     */
    const val NAME: String = "emb.state.name"

    /**
     * The value a change event's state took.
     */
    const val NEW_VALUE: String = "emb.state.new_value"

    /**
     * [SYSTEM], if the SDK generated the value a change event's state took.
     */
    const val NEW_VALUE_TYPE: String = "emb.state.new_value_type"

    /**
     * What a value's type is when the SDK generated the value, rather than observed it from the app.
     */
    const val SYSTEM: String = "system"

    /**
     * The kind of thing an event's states belong to.
     */
    const val ENTITY_TYPE: String = "emb.entity.type"

    /**
     * Which one of that kind: for [PROCESS_ENTITY], the SDK's identifier for the process.
     */
    const val ENTITY_ID: String = "emb.entity.id"

    /**
     * The [ENTITY_TYPE] of states that belong to an instance of the app's process.
     */
    const val PROCESS_ENTITY: String = "process"

    /**
     * The name of the event emitted for a state change.
     */
    const val CHANGE_EVENT: String = "emb.state.change"

    /**
     * The name of the event emitted with every state's value when a session part starts.
     */
    const val SNAPSHOT_EVENT: String = "emb.state.snapshot"
}
