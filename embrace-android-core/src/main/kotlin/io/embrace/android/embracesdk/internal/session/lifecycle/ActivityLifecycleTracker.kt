package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks activity lifecycle events.
 */
class ActivityLifecycleTracker(
    private val application: Application,
) : ActivityTracker {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * The currently active activity.
     */
    @Volatile
    private var currentActivity = WeakReference<Activity?>(null)

    /**
     * This method will update the current activity for further checking.
     *
     * @param activity the activity involved in the state change.
     */

    @Synchronized
    fun updateStateWithActivity(activity: Activity?) {
        currentActivity = WeakReference(activity)
    }

    /**
     * Returns the current activity instance
     */
    override val foregroundActivity: Activity?
        get() {
            val foregroundActivity = currentActivity.get()
            if (foregroundActivity == null || foregroundActivity.isFinishing) {
                return null
            }
            return foregroundActivity
        }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        updateStateWithActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        updateStateWithActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun close() {
        runCatching {
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }
}
