package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.otel.state.StateAttributes
import io.embrace.android.embracesdk.internal.otel.state.StateStamp
import io.embrace.android.embracesdk.internal.session.persistence.StateValue

/**
 * The value of every state at one moment, and [sequence], the place of that moment in the order of state changes.
 * [sinceMs] is when the change numbered [sequence] was recorded, which is when these values took effect, where the
 * recorder notes that, and 0 where it does not.
 *
 * It never changes, so everything read from one instance describes the same moment.
 */
class StateSnapshot internal constructor(
    override val sequence: Long,
    internal val values: Map<String, StateValue>,
    override val sinceMs: Long = 0,
) : StateStamp {

    /**
     * Every state's value as one string: `name=value` for each state, joined by `;`. A value the SDK generated, rather
     * than observed from the app, is written `name~value`. A `;`, `=`, `~` or `\` inside a name or a value is preceded by
     * a `\`. Built the first time it is read and kept.
     */
    val encoded: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString {
            values.values.forEach { value ->
                if (isNotEmpty()) {
                    append(PAIR_SEPARATOR)
                }
                appendEscaped(value.state)
                append(if (value.system) SYSTEM_VALUE_SEPARATOR else VALUE_SEPARATOR)
                appendEscaped(value.value_)
            }
        }
    }

    /**
     * Every state's value as plain attributes, one for each state and one more for a value the SDK generated. Built the
     * first time it is read and kept, so that everything stamped until the next change shares one map.
     */
    override val attributes: Map<String, String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val attributes = LinkedHashMap<String, String>(values.size)
        values.values.forEach { value ->
            attributes[StateAttributes.VALUE_PREFIX + value.state] = value.value_
            if (value.system) {
                attributes[StateAttributes.VALUE_TYPE_PREFIX + value.state] = StateAttributes.SYSTEM
            }
        }
        attributes
    }

    override fun endAttributes(start: StateStamp): Map<String, String> {
        val before = (start as? StateSnapshot)?.values.orEmpty()
        if (before === values) {
            return emptyMap()
        }
        val attributes = LinkedHashMap<String, String>()
        values.values.forEach { value ->
            val previous = before[value.state]
            if (previous == null || previous.value_ != value.value_ || previous.system != value.system) {
                attributes[StateAttributes.END_VALUE_PREFIX + value.state] = value.value_
                if (value.system) {
                    attributes[StateAttributes.END_VALUE_TYPE_PREFIX + value.state] = StateAttributes.SYSTEM
                }
            }
        }
        return attributes
    }

    /**
     * Whether no state has reported a value yet.
     */
    fun isEmpty(): Boolean = values.isEmpty()

    /**
     * This snapshot with [value] in place of whatever its state held, as of [sequence], recorded at [sinceMs].
     */
    internal fun with(sequence: Long, value: StateValue, sinceMs: Long = this.sinceMs): StateSnapshot {
        val updated = LinkedHashMap<String, StateValue>(values)
        updated[value.state] = value
        return StateSnapshot(sequence, updated, sinceMs)
    }

    private fun StringBuilder.appendEscaped(text: String) {
        for (char in text) {
            if (char in ESCAPED) {
                append(ESCAPE)
            }
            append(char)
        }
    }

    companion object {
        /**
         * The attribute a snapshot is stamped under as [encoded].
         */
        const val ATTRIBUTE: String = "emb.states"

        /**
         * The attribute a snapshot's [sequence] is stamped under.
         */
        const val SEQUENCE_ATTRIBUTE: String = StateAttributes.SEQUENCE

        internal val NONE = StateSnapshot(0, emptyMap())

        private const val PAIR_SEPARATOR = ';'
        private const val VALUE_SEPARATOR = '='
        private const val SYSTEM_VALUE_SEPARATOR = '~'
        private const val ESCAPE = '\\'
        private const val ESCAPED = "$PAIR_SEPARATOR$VALUE_SEPARATOR$SYSTEM_VALUE_SEPARATOR$ESCAPE"
    }
}
