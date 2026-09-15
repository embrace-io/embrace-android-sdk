package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Tracks Activities coming into and out of view through [Application.ActivityLifecycleCallbacks], but listens to [ProcessStateListener]
 * when it comes to tracking app backgrounding in order to synchronize with the rest of the SDK's app backgrounding logic.
 *
 * The time that this component's listeners fire is the canonical time for the signal, whenever it is processed downstream.
 */
internal class ActivityNavigationTracker(
    private val clock: Clock,
    private val eventBus: EventBus,
    private val navigationTrackingService: NavigationTrackingService,
) : Application.ActivityLifecycleCallbacks, ProcessStateListener {

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
        eventBus.emit(Backgrounded(clock.now()))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onForeground() {}

    private fun handleActivityStarted(activity: Activity) {
        eventBus.emit(ActivityStarted(activity.getId(), clock.now()))
    }

    private fun handleActivityResumed(activity: Activity) {
        eventBus.emit(ActivityResumed(activity.getId(), activity.localClassName, clock.now()))

        // Add screen source tracking after the resume signal is emitted to mimic how the rememberNavController Composable will do it.
        navigationTrackingService.trackNavigation(activity)
    }

    private fun handleActivityPaused(activity: Activity) {
        eventBus.emit(ActivityPaused(activity.getId(), clock.now()))
    }
}
