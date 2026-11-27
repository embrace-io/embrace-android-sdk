package io.embrace.android.embracesdk.internal.arch.schema

/**
 * The names v2 of the runtime state data model puts on the wire. Every key is under `emb.app.state.`, and a key that
 * holds a state's name puts it last.
 *
 * These are written by hand until the semantic conventions work confirms them and they can be generated.
 */
object StateKeys {

    private const val STEM = "emb.app.state."

    /** The sequence number: the last change before a log or a span's start, or a change's own number. */
    const val SEQ: String = "${STEM}seq"

    /** The last change before a span ended. */
    const val END_SEQ: String = "${STEM}end_seq"

    /** Every state's value packed into one string, on a log in Embrace's own payload. */
    const val PACKED: String = "${STEM}packed"

    const val NEW_VALUE: String = "${STEM}new_value"
    const val NEW_VALUE_TYPE: String = "${STEM}new_value_type"
    const val PREVIOUS_VALUE: String = "${STEM}previous_value"
    const val PREVIOUS_VALUE_TYPE: String = "${STEM}previous_value_type"
    const val NAME: String = "${STEM}name"

    const val INITIAL_SEQ: String = "${STEM}initial_seq"
    const val DROPPED_CHANGES: String = "${STEM}dropped_changes"
    const val GLOBAL_CHANGE_LIMIT: String = "${STEM}global_change_limit"
    const val TRUNCATED: String = "${STEM}truncated"

    /** The value a generated value's type key holds. */
    const val SYSTEM: String = "system"

    fun value(name: String): String = "${STEM}value.$name"
    fun valueType(name: String): String = "${STEM}value_type.$name"
    fun endValue(name: String): String = "${STEM}end_value.$name"
    fun endValueType(name: String): String = "${STEM}end_value_type.$name"
    fun initialValue(name: String): String = "${STEM}initial_value.$name"
    fun initialValueType(name: String): String = "${STEM}initial_value_type.$name"
    fun notInSessionCount(name: String): String = "${STEM}not_in_session_count.$name"
    fun droppedCount(name: String): String = "${STEM}dropped_count.$name"
    fun changeLimit(name: String): String = "${STEM}change_limit.$name"
    fun finalValue(name: String): String = "${STEM}final_value.$name"
    fun finalValueType(name: String): String = "${STEM}final_value_type.$name"
    fun finalSeq(name: String): String = "${STEM}final_seq.$name"

    /** The name of the span that holds a session part's change log. */
    const val CHANGE_LOG_SPAN_NAME: String = "emb-states"

    /** The name of the event for one change of the state [name] in the change log. */
    fun changeEventName(name: String): String = "emb-state-$name"

    /** The event name of a change replayed to a customer's exporter. */
    const val CHANGE_EVENT: String = "${STEM}change"

    /** The event name of every state's value sent to a customer's exporter when a session part starts. */
    const val SNAPSHOT_EVENT: String = "${STEM}snapshot"

    const val ENTITY_TYPE: String = "emb.entity.type"
    const val ENTITY_ID: String = "emb.entity.id"
    const val ENTITY_TYPE_PROCESS: String = "process"

    /** Whether a key belongs to the SDK, so that a change's own attributes may never use it. */
    fun isReserved(key: String): Boolean = key.startsWith(STEM) || key.startsWith("emb.state.")

    /** Whether a key carries a state's value for a customer's exporter in the verbose rendering of a span. */
    fun isSpanValueKey(key: String): Boolean = key.startsWith(STEM) && (
        key.startsWith("${STEM}value.") || key.startsWith("${STEM}value_type.") ||
            key.startsWith("${STEM}end_value.") || key.startsWith("${STEM}end_value_type.")
        )
}
