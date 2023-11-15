package io.embrace.android.embracesdk.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.utils.stream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks activity lifecycle events.
 */
internal class ActivityLifecycleTracker(
    private val application: Application,
    private val orientationService: OrientationService,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : ActivityTracker {

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * List of listeners that subscribe to activity events.
     */
    @VisibleForTesting
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
    @VisibleForTesting
    @Synchronized
    fun updateStateWithActivity(activity: Activity?) {
        logger.logDeveloper(
            "EmbraceActivityService", "Current activity: " + getActivityName(activity)
        )
        currentActivity = WeakReference(activity)
    }

    /**
     * Returns the current activity instance
     */
    override val foregroundActivity: Activity?
        get() {
            val foregroundActivity = currentActivity.get()
            if (foregroundActivity == null || foregroundActivity.isFinishing) {
                logger.logDeveloper(
                    "EmbraceActivityService", "Foreground activity not present"
                )
                return null
            }
            logger.logDeveloper(
                "EmbraceActivityService",
                "Foreground activity name: " + getActivityName(foregroundActivity)
            )
            return foregroundActivity
        }

    /**
     * This method will update the current activity orientation.
     *
     * @param activity the activity involved in the tracking orientation process.
     */
    private fun updateOrientationWithActivity(activity: Activity) {
        try {
            logger.logDeveloper(
                "EmbraceActivityService",
                "Updated orientation: " + activity.resources.configuration.orientation
            )
            orientationService.onOrientationChanged(activity.resources.configuration.orientation)
        } catch (ex: Exception) {
            logger.logDebug("Failed to register an orientation change", ex)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        logger.logDeveloper(
            "ActivityLifecycleTracker", "Activity created: " + getActivityName(activity)
        )
        updateStateWithActivity(activity)
        updateOrientationWithActivity(activity)
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onActivityCreated(activity, bundle)
            } catch (ex: Exception) {
                logger.logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        logger.logDeveloper(
            "ActivityLifecycleTracker", "Activity started: " + getActivityName(activity)
        )
        updateStateWithActivity(activity)
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onView(activity)
            } catch (ex: Exception) {
                logger.logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        logger.logDeveloper(
            "ActivityLifecycleTracker", "Activity resumed: " + getActivityName(activity)
        )
        if (!activity.javaClass.isAnnotationPresent(StartupActivity::class.java)) {
            // If the activity coming to foreground doesn't have the StartupActivity annotation
            // the the SDK will finalize any pending startup moment.
            logger.logDeveloper(
                "ActivityLifecycleTracker", "Activity resumed: " + getActivityName(activity)
            )
            stream(listeners) { listener: ActivityLifecycleListener ->
                try {
                    listener.applicationStartupComplete()
                } catch (ex: Exception) {
                    logger.logDebug(ERROR_FAILED_TO_NOTIFY, ex)
                }
            }
        } else {
            logger.logDeveloper(
                "ActivityLifecycleTracker", getActivityName(activity) + " is @StartupActivity"
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        logger.logDeveloper(
            "ActivityLifecycleTracker", "Activity stopped: " + getActivityName(activity)
        )
        stream(listeners) { listener: ActivityLifecycleListener ->
            try {
                listener.onViewClose(activity)
            } catch (ex: Exception) {
                logger.logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun getActivityName(activity: Activity?): String {
        return activity?.localClassName ?: "Null"
    }

    override fun addListener(listener: ActivityLifecycleListener) {
        // assumption: we always need to run the Session service first, then everything else,
        // because otherwise the session envelope will not be created. The ActivityListener
        // could use separating from session handling, but that's a bigger change.
        val priority = listener is SessionService
        if (!listeners.contains(listener)) {
            if (priority) {
                listeners.add(0, listener)
            } else {
                listeners.addIfAbsent(listener)
            }
        }
    }

    override fun close() {
        try {
            logger.logDebug("Shutting down EmbraceActivityService")
            application.unregisterActivityLifecycleCallbacks(this)
            listeners.clear()
        } catch (ex: Exception) {
            logger.logDebug("Error when closing EmbraceActivityService", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify ActivityLifecycleTracker listener"
    }
}
