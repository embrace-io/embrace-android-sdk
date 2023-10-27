package io.embrace.android.embracesdk.session

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.utils.ThreadUtils
import io.embrace.android.embracesdk.utils.stream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current activity and background state, and dispatching events to other
 * services as required.
 *
 * See activity lifecycle documentation
 * [here](https://developer.android.com/guide/components/activities/activity-lifecycle).
 *
 * We only need to track the `onForeground` and `onActivityPaused` lifecycle hooks for background
 * detection, as these are the only two which are consistently fired in all scenarios.
 */
internal class EmbraceActivityService(

    /**
     * The application.
     */
    private val application: Application,

    /**
     * The orientation service.
     */
    private val orientationService: OrientationService?,
    private val clock: Clock
) : ActivityService {

    /**
     * The memory service, it's provided on the instantiation of the service.
     */
    private var memoryService: MemoryService? = null

    /**
     * List of listeners that subscribe to activity events.
     */
    @VisibleForTesting
    val listeners = CopyOnWriteArrayList<ActivityListener>()

    /**
     * The currently active activity.
     */
    @Volatile
    private var currentActivity = WeakReference<Activity?>(null)

    /**
     * States if the activity foreground phase comes from a cold start or not.
     * Checked every time an activity executes a foreground phase.
     */
    @Volatile
    private var coldStart = true

    /**
     * States the initialization time of the EmbraceActivityService, inferring it is initialized
     * from the [Embrace.start] method.
     */
    private val startTime: Long = clock.now()

    /**
     * Returns if the app's in background or not.
     */
    @Volatile
    override var isInBackground = true
        private set

    init {
        application.registerActivityLifecycleCallbacks(this)
        application.applicationContext.registerComponentCallbacks(this)
        // add lifecycle observer on main thread to avoid IllegalStateExceptions with
        // androidx.lifecycle
        ThreadUtils.runOnMainThread(
            Runnable {
                ProcessLifecycleOwner.get().lifecycle
                    .addObserver(this@EmbraceActivityService)
            }
        )
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        logDeveloper("EmbraceActivityService", "Activity created: " + getActivityName(activity))
        updateStateWithActivity(activity)
        updateOrientationWithActivity(activity)
        stream(listeners) { listener: ActivityListener ->
            try {
                listener.onActivityCreated(activity, bundle)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        logDeveloper("EmbraceActivityService", "Activity started: " + getActivityName(activity))
        updateStateWithActivity(activity)
        stream(listeners) { listener: ActivityListener ->
            try {
                listener.onView(activity)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        logDeveloper("EmbraceActivityService", "Activity resumed: " + getActivityName(activity))
        if (!activity.javaClass.isAnnotationPresent(StartupActivity::class.java)) {
            // If the activity coming to foreground doesn't have the StartupActivity annotation
            // the the SDK will finalize any pending startup moment.
            logDeveloper("EmbraceActivityService", "Activity resumed: " + getActivityName(activity))
            stream(listeners) { listener: ActivityListener ->
                try {
                    listener.applicationStartupComplete()
                } catch (ex: Exception) {
                    logDebug(ERROR_FAILED_TO_NOTIFY, ex)
                }
            }
        } else {
            logDeveloper(
                "EmbraceActivityService",
                getActivityName(activity) + " is @StartupActivity"
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        logDeveloper("EmbraceActivityService", "Activity stopped: " + getActivityName(activity))
        stream(listeners) { listener: ActivityListener ->
            try {
                listener.onViewClose(activity)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * This method will update the current activity for further checking.
     *
     * @param activity the activity involved in the state change.
     */
    @VisibleForTesting
    @Synchronized
    fun updateStateWithActivity(activity: Activity?) {
        logDeveloper("EmbraceActivityService", "Current activity: " + getActivityName(activity))
        currentActivity = WeakReference(activity)
    }

    /**
     * This method will update the current activity orientation.
     *
     * @param activity the activity involved in the tracking orientation process.
     */
    private fun updateOrientationWithActivity(activity: Activity) {
        if (orientationService != null) {
            try {
                logDeveloper(
                    "EmbraceActivityService",
                    "Updated orientation: " + activity.resources.configuration.orientation
                )
                orientationService.onOrientationChanged(activity.resources.configuration.orientation)
            } catch (ex: Exception) {
                logDebug("Failed to register an orientation change", ex)
            }
        }
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON START.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    override fun onForeground() {
        logDebug("AppState: App entered foreground.")
        isInBackground = false
        val timestamp = clock.now()
        stream<ActivityListener>(listeners) { listener: ActivityListener ->
            try {
                listener.onForeground(coldStart, startTime, timestamp)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
        coldStart = false
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON STOP.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    override fun onBackground() {
        logDebug("AppState: App entered background")
        updateStateWithActivity(null)
        isInBackground = true
        val timestamp = clock.now()
        stream<ActivityListener>(listeners) { listener: ActivityListener ->
            try {
                InternalStaticEmbraceLogger.logger.logWarning("onBackground() listener: $listener")
                listener.onBackground(timestamp)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    /**
     * Called when the OS has determined that it is a good time for a process to trim unneeded
     * memory.
     *
     * @param trimLevel the context of the trim, giving a hint of the amount of trimming.
     */
    override fun onTrimMemory(trimLevel: Int) {
        logDeveloper("EmbraceActivityService", "onTrimMemory(). TrimLevel: $trimLevel")
        if (trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                memoryService?.onMemoryWarning()
            } catch (ex: Exception) {
                logDebug("Failed to handle onTrimMemory (low memory) event", ex)
            }
        }
    }

    fun setMemoryService(memoryService: MemoryService?) {
        this.memoryService = memoryService
    }

    override fun onConfigurationChanged(configuration: Configuration) {}
    override fun onLowMemory() {}

    /**
     * Returns the current activity instance
     */
    override val foregroundActivity: Activity?
        get() {
            val foregroundActivity = currentActivity.get()
            if (foregroundActivity == null || foregroundActivity.isFinishing) {
                logDeveloper("EmbraceActivityService", "Foreground activity not present")
                return null
            }
            logDeveloper(
                "EmbraceActivityService",
                "Foreground activity name: " + getActivityName(foregroundActivity)
            )
            return foregroundActivity
        }

    override fun addListener(listener: ActivityListener) {
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
            logDebug("Shutting down EmbraceActivityService")
            application.applicationContext.unregisterComponentCallbacks(this)
            application.unregisterActivityLifecycleCallbacks(this)
            listeners.clear()
        } catch (ex: Exception) {
            logDebug("Error when closing EmbraceActivityService", ex)
        }
    }

    private fun getActivityName(activity: Activity?): String {
        return activity?.localClassName ?: "Null"
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify EmbraceActivityService listener"
    }
}
