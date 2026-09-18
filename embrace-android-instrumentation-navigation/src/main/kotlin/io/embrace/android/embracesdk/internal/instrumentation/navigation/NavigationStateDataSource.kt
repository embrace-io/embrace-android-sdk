package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

/**
 * Reports the screen published against [CurrentScreen.KEY] as navigation state, knowing nothing about where it was resolved.
 */
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen(name = INITIALIZING),
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
) {

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.eventBus.addStateHandler(CurrentScreen.KEY) { screen ->
            onStateChange(newState = Screen(name = screen.name), transitionTimeMs = screen.sinceMs)
        }
    }

    companion object {
        private const val INITIALIZING = "Initializing"
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000
    }
}
