package io.embrace.android.embracesdk.internal.state

/**
 * The names the state data model puts on the wire. Every attribute key is under [STEM], and a key that holds a state's name puts it
 * last.
 *
 * These are written by hand until they can be generated from the semantic conventions registry.
 */
object AppStateKeys {

    /** The stem every key of the state data model is under. */
    const val STEM: String = "emb.app.state."

    /** The stem the first state data model's keys are under, which a change's own attributes may not use either. */
    const val LEGACY_STEM: String = "emb.state."

    /** On a log sent to Embrace: every state's value, packed into one string by [PackedStates]. */
    const val PACKED: String = "emb.app.state.packed"

    /** On a log, a span or a change: the number of the last change before it, or of the change itself. */
    const val SEQ: String = "emb.app.state.seq"

    /** On a span: the number of the last change before it ended, when that differs from [SEQ]. */
    const val END_SEQ: String = "emb.app.state.end_seq"

    const val VALUE_PREFIX: String = "emb.app.state.value."
    const val VALUE_TYPE_PREFIX: String = "emb.app.state.value_type."
    const val END_VALUE_PREFIX: String = "emb.app.state.end_value."
    const val END_VALUE_TYPE_PREFIX: String = "emb.app.state.end_value_type."

    const val INITIAL_SEQ: String = "emb.app.state.initial_seq"
    const val INITIAL_VALUE_PREFIX: String = "emb.app.state.initial_value."
    const val INITIAL_VALUE_TYPE_PREFIX: String = "emb.app.state.initial_value_type."
    const val NOT_IN_SESSION_COUNT_PREFIX: String = "emb.app.state.not_in_session_count."
    const val DROPPED_CHANGES: String = "emb.app.state.dropped_changes"
    const val DROPPED_COUNT_PREFIX: String = "emb.app.state.dropped_count."
    const val CHANGE_LIMIT_PREFIX: String = "emb.app.state.change_limit."
    const val GLOBAL_CHANGE_LIMIT: String = "emb.app.state.global_change_limit"
    const val FINAL_VALUE_PREFIX: String = "emb.app.state.final_value."
    const val FINAL_VALUE_TYPE_PREFIX: String = "emb.app.state.final_value_type."
    const val FINAL_SEQ_PREFIX: String = "emb.app.state.final_seq."
    const val TRUNCATED: String = "emb.app.state.truncated"

    const val NAME: String = "emb.app.state.name"
    const val NEW_VALUE: String = "emb.app.state.new_value"
    const val NEW_VALUE_TYPE: String = "emb.app.state.new_value_type"
    const val PREVIOUS_VALUE: String = "emb.app.state.previous_value"
    const val PREVIOUS_VALUE_TYPE: String = "emb.app.state.previous_value_type"

    /** The value of every `*_type` key, written only beside a value the SDK generated. */
    const val VALUE_TYPE_SYSTEM: String = "system"

    /** The name of the one span that holds a session part's change log. */
    const val CHANGE_LOG_SPAN_NAME: String = "emb-states"

    /** What a change log event's name starts with, followed by the name of the state that changed. */
    const val CHANGE_LOG_EVENT_PREFIX: String = "emb-state-"

    /** The event name of the log record a customer's exporter gets for a change. */
    const val CHANGE_EVENT_NAME: String = "emb.app.state.change"

    /** The event name of the log record a customer's exporter gets when a session part starts. */
    const val SNAPSHOT_EVENT_NAME: String = "emb.app.state.snapshot"

    const val ENTITY_TYPE: String = "emb.entity.type"
    const val ENTITY_ID: String = "emb.entity.id"
    const val ENTITY_TYPE_PROCESS: String = "process"

    /** The process identifier, which is in the OpenTelemetry Resource when the state data model is on. */
    const val PROCESS_IDENTIFIER: String = "emb.process_identifier"

    /**
     * Whether [key] is one only the SDK may write, so that a change's own attribute under it is dropped.
     */
    fun isReserved(key: String): Boolean = key.startsWith(STEM) || key.startsWith(LEGACY_STEM)

    /**
     * Whether [key] holds a state's value in the form a customer's exporter gets, which Embrace's own payload leaves out.
     */
    fun isCustomerValueKey(key: String): Boolean =
        key.startsWith(VALUE_PREFIX) ||
            key.startsWith(VALUE_TYPE_PREFIX) ||
            key.startsWith(END_VALUE_PREFIX) ||
            key.startsWith(END_VALUE_TYPE_PREFIX)
}
