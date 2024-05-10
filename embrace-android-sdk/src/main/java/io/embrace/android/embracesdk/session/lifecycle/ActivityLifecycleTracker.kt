package io.embrace.android.embracesdk.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.utils.stream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks activity lifecycle events.
 */
internal class ActivityLifecycleTracker(
    private val application: Application,
    private val orientationService: OrientationService,
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

    /**
     * This method will update the current activity orientation.
     *
     * @param activity the activity involved in the tracking orientation process.
     */
    private fun updateOrientationWithActivity(activity: Activity) {
        try {
            orientationService.onOrientationChanged(activity.resources.configuration.orientation)
        } catch (ex: Exception) {
            logger.logWarning("Failed to register an orientation change", ex)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        updateStateWithActivity(activity)
        updateOrientationWithActivity(activity)
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityCreated(activity, bundle)
            } catch (ex: Exception) {
                logger.logWarning(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        updateStateWithActivity(activity)
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onView(activity)
            } catch (ex: Exception) {
                logger.logWarning(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!activity.javaClass.isAnnotationPresent(StartupActivity::class.java)) {
            // If the activity coming to foreground doesn't have the StartupActivity annotation
            // the the SDK will finalize any pending startup moment.
            stream(listeners) { listener: ActivityLifecycleListener ->
                try {
                    listener.applicationStartupComplete()
                } catch (ex: Exception) {
                    logger.logWarning(ERROR_FAILED_TO_NOTIFY, ex)
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
                logger.logWarning(ERROR_FAILED_TO_NOTIFY, ex)
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

    override fun close() {
        try {
            logger.logDebug("Shutting down EmbraceActivityService")
            application.unregisterActivityLifecycleCallbacks(this)
            listeners.clear()
        } catch (ex: Exception) {
            logger.logWarning("Error when closing EmbraceActivityService", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify ActivityLifecycleTracker listener"
    }
}
