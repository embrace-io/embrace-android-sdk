package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The [value] the state named [state] was observed to hold at [timeMs], emitted on the event bus by the data source that
 * declared the state with a [StateDeclaration]. It need not differ from the value the state already had: whatever orders
 * these updates decides which are changes. An update for a state that has not been declared is discarded.
 *
 * @param attributes attributes of the change. Only those under a key the state's declaration names are kept.
 * @param droppedChanges how many changes of this state its reporter knows were made since its last update and did not
 * report. They are counted for the state, and have no place in the order of changes. An update may repeat the state's
 * value to report them.
 */
class StateUpdate(
    val state: String,
    val value: Any,
    val timeMs: Long,
    val attributes: Map<String, String> = emptyMap(),
    val droppedChanges: Int = 0,
) {
    companion object {
        /**
         * The channel state updates are emitted on, shared by everything that reports or records them.
         */
        val KEY = EventKey<StateUpdate>()
    }
}
