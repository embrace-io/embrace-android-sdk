package io.embrace.android.embracesdk.internal.arch.navigation

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.utils.event.StateKey

/**
 * The screen currently shown, resolved from the [NavigationSignal] stream.
 */
data class CurrentScreen(val screen: Screen, val sinceMs: Long) {

    companion object {
        /**
         * The state this is the value of, shared by everything publishing or reading the current screen.
         */
        val KEY = StateKey<CurrentScreen>()
    }
}
