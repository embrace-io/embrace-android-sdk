package io.embrace.android.embracesdk.internal.otel.state

/**
 * The names telemetry is associated with state under.
 */
object StateAttributes {

    /**
     * The number of the last state change before a span started, before a log was emitted, or that an event describes.
     */
    const val SEQUENCE: String = "emb.app.state.seq"

    /**
     * The number of the last state change before a span ended, where that is not [SEQUENCE].
     */
    const val END_SEQUENCE: String = "emb.app.state.end_seq"

    /**
     * Every state's value in one string, `name=value` for each state joined by `;`, which is how a log carries state in
     * Embrace's own payload. A value the SDK generated is written `name~value`. A `;`, `=`, `~` or `\` inside a name or a
     * value is preceded by a `\`.
     */
    const val STATES: String = "emb.app.state.packed"

    /**
     * Followed by a state's name: its value.
     */
    const val VALUE_PREFIX: String = "emb.app.state.value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value.
     */
    const val VALUE_TYPE_PREFIX: String = "emb.app.state.value_type."

    /**
     * Followed by a state's name: its value when a span ended, where that is not its value when the span started.
     */
    const val END_VALUE_PREFIX: String = "emb.app.state.end_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a span ended.
     */
    const val END_VALUE_TYPE_PREFIX: String = "emb.app.state.end_value_type."

    /**
     * Followed by a state's name: its value when a session part's change log opened.
     */
    const val INITIAL_VALUE_PREFIX: String = "emb.app.state.initial_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a session part's change log opened.
     */
    const val INITIAL_VALUE_TYPE_PREFIX: String = "emb.app.state.initial_value_type."

    /**
     * Followed by a state's name: its value when a session part ended, as its change log records it for a state that had
     * a change refused by a limit.
     */
    const val FINAL_VALUE_PREFIX: String = "emb.app.state.final_value."

    /**
     * Followed by a state's name: [SYSTEM], if the SDK generated its value when a session part ended.
     */
    const val FINAL_VALUE_TYPE_PREFIX: String = "emb.app.state.final_value_type."

    /**
     * Followed by a state's name: the number of the state's last change in a session part, for a state that had a change
     * refused by a limit. The state's final value holds from that number to the part's end.
     */
    const val FINAL_SEQUENCE_PREFIX: String = "emb.app.state.final_seq."

    /**
     * The number of the last state change that the opening values of a session part's change log are as of.
     */
    const val INITIAL_SEQUENCE: String = "emb.app.state.initial_seq"

    /**
     * The number of the last state change made before a session part started, which is where its change log says the part
     * began.
     */
    const val PART_START_SEQUENCE: String = "emb.app.state.part_start_seq"

    /**
     * How many changes were folded into the opening values of a change log, which are made before them and not listed.
     */
    const val FOLDED_CHANGES: String = "emb.app.state.folded_changes"

    /**
     * How many changes made during a session part took a number and are not listed in its change log, for every state
     * together, because a limit refused them: a state's own, or the one the whole change log has.
     */
    const val DROPPED_CHANGES: String = "emb.app.state.dropped_changes"

    /**
     * Followed by a state's name: how many of its changes a change log does not list. Those are the changes a limit
     * refused, and the changes the state's instrumentation reported that it never passed on.
     */
    const val DROPPED_COUNT_PREFIX: String = "emb.app.state.dropped_count."

    /**
     * Followed by a state's name: the most changes of that state one session part's change log lists, where that limit
     * refused a change of the state during the part.
     */
    const val CHANGE_LIMIT_PREFIX: String = "emb.app.state.change_limit."

    /**
     * The most changes one session part's change log lists for every state together, counting those made before the part
     * that it opens with, where that limit refused a change during the part.
     */
    const val GLOBAL_CHANGE_LIMIT: String = "emb.app.state.global_change_limit"

    /**
     * Set to true on a change log that stops short: the process died with the part in progress, or the log could not be
     * written to or read back in full.
     */
    const val TRUNCATED: String = "emb.app.state.truncated"

    /**
     * The name of the state a change event is about.
     */
    const val NAME: String = "emb.app.state.name"

    /**
     * The value a change event's state took.
     */
    const val NEW_VALUE: String = "emb.app.state.new_value"

    /**
     * [SYSTEM], if the SDK generated the value a change event's state took.
     */
    const val NEW_VALUE_TYPE: String = "emb.app.state.new_value_type"

    /**
     * The value a change event's state had before the change, which is the value its own attributes describe. Absent on a
     * state's first change.
     */
    const val PREVIOUS_VALUE: String = "emb.app.state.previous_value"

    /**
     * [SYSTEM], if the SDK generated the value a change event's state had before the change.
     */
    const val PREVIOUS_VALUE_TYPE: String = "emb.app.state.previous_value_type"

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
    const val CHANGE_EVENT: String = "emb.app.state.change"

    /**
     * The name of the event emitted with every state's value when a session part starts.
     */
    const val SNAPSHOT_EVENT: String = "emb.app.state.snapshot"
}
