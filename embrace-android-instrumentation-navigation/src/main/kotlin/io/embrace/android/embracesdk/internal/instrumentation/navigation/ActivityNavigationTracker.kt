package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import java.util.concurrent.atomic.AtomicInteger

internal class ActivityNavigationTracker(
    private val onEvent: (NavigationEvent) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    /**
     * A ref count of how many activities have been started and not stopped. If this number is greater than one it means there
     * is still an activity that is visible.
     */
    private var startedActivityCount = AtomicInteger(0)
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

    override fun onActivityPostStopped(activity: Activity) {
        if (usePrePostCallbacks) {
            checkForBackgrounded()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount.decrementAndGet()
        if (!usePrePostCallbacks) {
            checkForBackgrounded()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun handleActivityStarted(activity: Activity) {
        startedActivityCount.incrementAndGet()
        onEvent(ActivityStarted(activity.localClassName))
    }

    private fun checkForBackgrounded() {
        if (startedActivityCount.get() <= 0) {
            onEvent(NavigationEvent.Backgrounded)
        }
    }
}
