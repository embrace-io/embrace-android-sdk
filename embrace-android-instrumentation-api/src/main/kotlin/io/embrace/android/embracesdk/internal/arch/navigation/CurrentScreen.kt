package io.embrace.android.embracesdk.internal.arch.navigation

import io.embrace.android.embracesdk.internal.utils.event.StateKey

/**
 * The screen currently shown, resolved from the [NavigationSignal] stream.
 */
data class CurrentScreen(val name: String, val sinceMs: Long) {

    companion object {
        /**
         * The state this is the value of, shared by everything publishing or reading the current screen.
         */
        val KEY = StateKey<CurrentScreen>()
    }
}
