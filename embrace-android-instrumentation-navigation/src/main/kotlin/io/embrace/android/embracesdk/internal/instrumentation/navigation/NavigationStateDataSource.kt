package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

/**
 * Updates the navigation state by listening to events
 */
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen(name = INITIALIZING),
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
),
    NavigationControllerEventListener {
    private val broker = NavigationEventBroker(
        onScreenLoad = ::onScreenLoad
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
        args.appStateTracker.addListener(activityNavigationTracker)
    }

    override fun onDataCaptureDisabled() {
        args.application.unregisterActivityLifecycleCallbacks(activityNavigationTracker)
    }

    override fun onControllerAttached(activity: Activity, timestampMs: Long) {
        broker.onEvent(NavigationEvent.NavControllerAttached(activity, timestampMs))
    }

    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {
        broker.onEvent(NavigationEvent.NavControllerDestinationChanged(activity, screenName, timestampMs))
    }

    fun onScreenLoad(loadTimeMs: Long, screenName: String) {
        onStateChange(loadTimeMs, Screen(name = screenName))
    }

    companion object {
        private const val INITIALIZING = "Initializing"
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000
    }
}
