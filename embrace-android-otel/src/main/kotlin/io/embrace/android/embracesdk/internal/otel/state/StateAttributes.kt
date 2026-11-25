package io.embrace.android.embracesdk.internal.otel.state

/**
 * The names telemetry is associated with state under.
 */
object StateAttributes {

    /**
     * The number of the last state change before a span started, before a log was emitted, or that an event describes.
     */
    const val SEQUENCE: String = "emb.states.seq"

    /**
     * The number of the last state change before a span ended, where that is not [SEQUENCE].
     */
    const val END_SEQUENCE: String = "emb.states.end_seq"

    /**
     * Every state's value in one string, `name=value` for each state joined by `;`, which is how a log carries state in
     * Embrace's own payload. A value the SDK generated is written `name~value`. A `;`, `=`, `~` or `\` inside a name or a
     * value is preceded by a `\`.
     */
    const val STATES: String = "emb.states.packed"

    /**
     * Followed by a state's name: its value.
     */
    const val VALUE_PREFIX: String = "emb.states.value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value.
     */
    const val VALUE_TYPE_PREFIX: String = "emb.states.value_type."

    /**
     * Followed by a state's name: its value when a span ended, where that is not its value when the span started.
     */
    const val END_VALUE_PREFIX: String = "emb.states.end_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a span ended.
     */
    const val END_VALUE_TYPE_PREFIX: String = "emb.states.end_value_type."

    /**
     * Followed by a state's name: its value when a session part's change log opened.
     */
    const val INITIAL_VALUE_PREFIX: String = "emb.states.initial_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a session part's change log opened.
     */
    const val INITIAL_VALUE_TYPE_PREFIX: String = "emb.states.initial_value_type."

    /**
     * Followed by a state's name: its value when a session part ended, as its change log records it.
     */
    const val FINAL_VALUE_PREFIX: String = "emb.states.final_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a session part ended.
     */
    const val FINAL_VALUE_TYPE_PREFIX: String = "emb.states.final_value_type."

    /**
     * The number of the last state change that the opening values of a session part's change log are as of.
     */
    const val INITIAL_SEQUENCE: String = "emb.states.initial_seq"

    /**
     * The number of the last state change made before a session part started, which is where its change log says the part
     * began.
     */
    const val PART_START_SEQUENCE: String = "emb.states.part_start_seq"

    /**
     * How many changes were folded into the opening values of a change log, which are made before them and not listed.
     */
    const val FOLDED_CHANGES: String = "emb.states.folded_changes"

    /**
     * How many changes a change log did not record because it was full.
     */
    const val DROPPED_CHANGES: String = "emb.states.dropped_changes"

    /**
     * Set to true on a change log that stops short: the process died with the part in progress, or the log could not be
     * written to or read back in full.
     */
    const val TRUNCATED: String = "emb.states.truncated"

    /**
     * The name of the state a change event is about.
     */
    const val NAME: String = "emb.states.name"

    /**
     * The value a change event's state took.
     */
    const val NEW_VALUE: String = "emb.states.new_value"

    /**
     * [SYSTEM], if the SDK generated the value a change event's state took.
     */
    const val NEW_VALUE_TYPE: String = "emb.states.new_value_type"

    /**
     * The value a change event's state had before the change, which is the value its own attributes describe. Absent on a
     * state's first change.
     */
    const val PREVIOUS_VALUE: String = "emb.states.previous_value"

    /**
     * [SYSTEM], if the SDK generated the value a change event's state had before the change.
     */
    const val PREVIOUS_VALUE_TYPE: String = "emb.states.previous_value_type"

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
    const val CHANGE_EVENT: String = "emb.states.change"

    /**
     * The name of the event emitted with every state's value when a session part starts.
     */
    const val SNAPSHOT_EVENT: String = "emb.states.snapshot"
}
