package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.stream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks activity lifecycle events.
 */
class ActivityLifecycleTracker(
    private val application: Application,
    private val logger: EmbLogger,
) : ActivityTracker {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * List of listeners that subscribe to activity events.
     */
    val activityListeners: CopyOnWriteArrayList<ActivityLifecycleListener> =
        CopyOnWriteArrayList<ActivityLifecycleListener>()

    /**
     * List of listeners notified when application startup is complete
     */
    val startupListeners: CopyOnWriteArrayList<StartupListener> = CopyOnWriteArrayList<StartupListener>()

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
        stream(activityListeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityCreated(activity, bundle)
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        updateStateWithActivity(activity)
        stream(activityListeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityStarted(activity)
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!activity.javaClass.isAnnotationPresent(StartupActivity::class.java)) {
            // If the activity coming to foreground doesn't have the StartupActivity annotation
            // the the SDK will finalize any pending startup moment.
            stream(startupListeners) { listener: StartupListener ->
                try {
                    listener.applicationStartupComplete()
                } catch (ex: Exception) {
                    logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
                }
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        stream(activityListeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityStopped(activity)
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun addListener(listener: ActivityLifecycleListener) {
        if (!activityListeners.contains(listener)) {
            activityListeners.addIfAbsent(listener)
        }
    }

    override fun addStartupListener(listener: StartupListener) {
        if (!startupListeners.contains(listener)) {
            startupListeners.addIfAbsent(listener)
        }
    }

    override fun close() {
        runCatching {
            application.unregisterActivityLifecycleCallbacks(this)
            activityListeners.clear()
            startupListeners.clear()
        }
    }
}
