package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The [value] the state named [state] was observed to hold at [timeMs], emitted on the event bus by whatever observes the
 * state. It need not differ from the value the state already had: whatever orders these updates decides which are changes.
 */
class StateUpdate(
    val state: String,
    val value: Any,
    val timeMs: Long,
    val attributes: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * The channel state updates are emitted on, shared by everything that reports or records them.
         */
        val KEY = EventKey<StateUpdate>()
    }
}
