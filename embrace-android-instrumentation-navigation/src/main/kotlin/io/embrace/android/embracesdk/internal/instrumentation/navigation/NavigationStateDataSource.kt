package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

/**
 * Tracks the user's navigation state in the app
 */
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen(name = INITIALIZING),
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
) {
    private val broker = NavigationEventBroker(
        clock = args.clock,
        looper = Looper.getMainLooper(),
        onScreenLoad = ::onScreenLoad
    )

    private val activityNavigationTracker = ActivityNavigationTracker(broker::queueEvent)

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.application.registerActivityLifecycleCallbacks(activityNavigationTracker)
        args.appStateTracker.addListener(activityNavigationTracker)
    }

    override fun onDataCaptureDisabled() {
        args.application.unregisterActivityLifecycleCallbacks(activityNavigationTracker)
    }

    fun onScreenLoad(loadTimeMs: Long, screenName: String) {
        onStateChange(loadTimeMs, Screen(name = screenName))
    }

    companion object {
        private const val INITIALIZING = "Initializing"
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000
    }
}
