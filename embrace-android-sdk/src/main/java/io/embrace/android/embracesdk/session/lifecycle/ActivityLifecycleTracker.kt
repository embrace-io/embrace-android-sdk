package io.embrace.android.embracesdk.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.utils.stream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks activity lifecycle events.
 */
internal class ActivityLifecycleTracker(
    private val application: Application,
    private val logger: EmbLogger
) : ActivityTracker {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * List of listeners that subscribe to activity events.
     */
    val listeners = CopyOnWriteArrayList<ActivityLifecycleListener>()

    /**
     * List of listeners notified when application startup is complete
     */
    val startupListeners = CopyOnWriteArrayList<StartupListener>()

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
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityCreated(activity, bundle)
            } catch (ex: Exception) {
                logger.logWarning(ERROR_FAILED_TO_NOTIFY)
                logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        updateStateWithActivity(activity)
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onView(activity)
            } catch (ex: Exception) {
                logger.logWarning(ERROR_FAILED_TO_NOTIFY)
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
                    logger.logWarning(ERROR_FAILED_TO_NOTIFY)
                    logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
                }
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onViewClose(activity)
            } catch (ex: Exception) {
                logger.logWarning(ERROR_FAILED_TO_NOTIFY)
                logger.trackInternalError(InternalErrorType.ACTIVITY_LISTENER_FAIL, ex)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun addListener(listener: ActivityLifecycleListener) {
        if (!listeners.contains(listener)) {
            listeners.addIfAbsent(listener)
        }
    }

    override fun addStartupListener(listener: StartupListener) {
        if (!startupListeners.contains(listener)) {
            startupListeners.addIfAbsent(listener)
        }
    }

    override fun close() {
        try {
            logger.logDebug("Shutting down ActivityLifecycleTracker")
            application.unregisterActivityLifecycleCallbacks(this)
            listeners.clear()
            startupListeners.clear()
        } catch (ex: Exception) {
            logger.logWarning("Error when closing ActivityLifecycleTracker", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY = "Failed to notify ActivityLifecycleTracker listener"
    }
}
