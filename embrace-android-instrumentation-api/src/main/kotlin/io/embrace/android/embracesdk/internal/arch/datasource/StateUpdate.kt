package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The [value] the state named [state] was observed to hold at [timeMs], emitted on the event bus by whatever observes the
 * state. It need not differ from the value the state already had: whatever orders these updates decides which are changes.
 *
 * @param maxChangesInPart the most changes of this state that one session part lists, which is the state's own limit. A
 * change made past it during a part still replaces the state's value, and is counted and not listed.
 * @param droppedChanges how many changes of this state its reporter knows were made since its last update and did not
 * report. They are counted for the state, and have no place in the order of changes. An update may repeat the state's
 * value to report them.
 */
class StateUpdate(
    val state: String,
    val value: Any,
    val timeMs: Long,
    val attributes: Map<String, String> = emptyMap(),
    val maxChangesInPart: Int = DEFAULT_MAX_CHANGES_IN_PART,
    val droppedChanges: Int = 0,
) {
    companion object {
        /**
         * The channel state updates are emitted on, shared by everything that reports or records them.
         */
        val KEY = EventKey<StateUpdate>()

        /**
         * The most changes of one state that a session part lists, unless the state's reporter gives another number.
         */
        const val DEFAULT_MAX_CHANGES_IN_PART: Int = 100
    }
}
