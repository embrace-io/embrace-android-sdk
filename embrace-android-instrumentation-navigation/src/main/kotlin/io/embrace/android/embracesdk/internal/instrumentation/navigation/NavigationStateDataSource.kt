package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Reports the screen published against [CurrentScreen.KEY] as navigation state, knowing nothing about where it was resolved.
 *
 * Disabling it also stops whatever publishes that screen: [activityCallbacks] and the [signalHandler] that resolves it.
 */
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
    private val activityCallbacks: Application.ActivityLifecycleCallbacks? = null,
    private val signalHandler: EventHandler<NavigationSignal>? = null,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen.Initializing,
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
) {

    private val currentScreenHandler = EventHandler<CurrentScreen> { current ->
        onStateChange(newState = current.screen, transitionTimeMs = current.sinceMs)
    }

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.eventBus.addStateHandler(key = CurrentScreen.KEY, handler = currentScreenHandler)
    }

    override fun onDataCaptureDisabled() {
        args.eventBus.removeStateHandler(CurrentScreen.KEY, currentScreenHandler)
        signalHandler?.let { args.eventBus.removeHandler(NavigationSignal.KEY, it) }
        activityCallbacks?.let(args.application::unregisterActivityLifecycleCallbacks)
    }

    override fun outgoingValueAttributes(value: Screen): Map<String, String> {
        if (value is SystemStateValue) {
            // no app screen was showing: collect only to discard what accrued, so it isn't attributed to the next screen
            args.navigationTrackingService.collectScreenAttributes { _, _ -> }
            return emptyMap()
        }
        val attributes = mutableMapOf<String, String>()
        args.navigationTrackingService.collectScreenAttributes(attributes::set)
        return attributes
    }

    companion object {
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000
    }
}
