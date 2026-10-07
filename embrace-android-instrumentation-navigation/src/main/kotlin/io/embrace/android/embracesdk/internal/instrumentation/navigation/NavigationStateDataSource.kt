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
    defaultValue = Screen.Initializing,
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
) {

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.eventBus.addStateHandler(CurrentScreen.KEY) { current ->
            onStateChange(newState = current.screen, transitionTimeMs = current.sinceMs)
        }
    }

    companion object {
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000
    }
}
