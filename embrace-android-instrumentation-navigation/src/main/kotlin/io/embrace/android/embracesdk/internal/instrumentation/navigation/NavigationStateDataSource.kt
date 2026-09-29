package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue

/**
 * Updates the navigation state by listening to events
 */
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen.Initializing,
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
),
    NavigationControllerEventListener {
    private val broker = NavigationEventBroker(
        onScreenLoad = ::onScreenLoad,
    )

    private val activityNavigationTracker = ActivityNavigationTracker(
        clock = args.clock,
        onEvent = broker::onEvent,
        navigationTrackingService = args.navigationTrackingService,
    )

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.navigationTrackingService.navigationControllerEventListener = this
        args.application.registerActivityLifecycleCallbacks(activityNavigationTracker)
        args.processStateTracker.addListener(activityNavigationTracker)
    }

    override fun onControllerAttached(activity: Activity, timestampMs: Long) {
        broker.onEvent(NavigationEvent.NavControllerAttached(activity, timestampMs))
    }

    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {
        broker.onEvent(NavigationEvent.NavControllerDestinationChanged(activity, screenName, timestampMs))
    }

    fun onScreenLoad(loadTimeMs: Long, screen: Screen) {
        onStateChange(newState = screen, transitionTimeMs = loadTimeMs)
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
