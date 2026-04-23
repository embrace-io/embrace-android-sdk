package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityPaused
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityResumed
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.Backgrounded

/**
 * Tracks Activities coming into and out of view through [Application.ActivityLifecycleCallbacks], but listens to [AppStateListener]
 * when it comes to tracking app backgrounding in order to synchronize with the rest of the SDK's app backgrounding logic.
 *
 * The time that this component's listeners fire is the canonical time for the event, whenever it is processed downstream.
 */
internal class ActivityNavigationTracker(
    private val clock: Clock,
    private val onEvent: (NavigationEvent) -> Unit,
    private val navigationTrackingService: NavigationTrackingService,
) : Application.ActivityLifecycleCallbacks, AppStateListener {

    private val usePrePostCallbacks = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun onActivityPreStarted(activity: Activity) {
        if (usePrePostCallbacks) {
            handleActivityStarted(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!usePrePostCallbacks) {
            handleActivityStarted(activity)
        }
    }

    override fun onActivityPreResumed(activity: Activity) {
        if (usePrePostCallbacks) {
            handleActivityResumed(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!usePrePostCallbacks) {
            handleActivityResumed(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (!usePrePostCallbacks) {
            handleActivityPaused(activity)
        }
    }

    override fun onActivityPostPaused(activity: Activity) {
        if (usePrePostCallbacks) {
            handleActivityPaused(activity)
        }
    }

    override fun onBackground() {
        onEvent(Backgrounded(clock.now()))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onForeground() {}

    private fun handleActivityStarted(activity: Activity) {
        onEvent(ActivityStarted(activity, clock.now()))
    }

    private fun handleActivityResumed(activity: Activity) {
        onEvent(ActivityResumed(activity, clock.now()))

        // Add NavController tracking after the resume event is fired to mimic how the rememberNavController Composable will do it.
        navigationTrackingService.trackNavigation(activity)
    }

    private fun handleActivityPaused(activity: Activity) {
        onEvent(ActivityPaused(activity, clock.now()))
    }
}
