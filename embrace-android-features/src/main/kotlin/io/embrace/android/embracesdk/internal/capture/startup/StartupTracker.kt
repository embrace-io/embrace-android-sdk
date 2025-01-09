package io.embrace.android.embracesdk.internal.capture.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.ui.DrawEventEmitter

/**
 * Component that captures various timestamps throughout the startup process and uses that information to log spans that approximates to
 * the canonical cold and warm "Time to First Display" metric.
 *
 * In the abstract, we want to be as close as possible to approximate the duration between the start of the app process, to when the first
 * frame is completely rendered in the first useful activity. Because of the different capabilities of each Android version, we are
 * measuring slightly different events during startup, so there will be several flavors the startup trace depending on which version of
 * Android the app is currently running on.
 *
 * Data this component provides will be used along side manually set our captured data by [AppStartupTraceEmitter] to create the final
 * traces.
 *
 * For approximating the first frame being completely drawn:
 *
 * - Android 10 onwards, a callback will with registered with the given [DrawEventEmitter] to be invoked when that component
 *   detects that the Activity instance has drawn its first frame.
 *
 * - Older Android versions that are supported, we just use when the first Activity was resumed. We will iterate on this in the future.
 */
class StartupTracker(
    private val appStartupDataCollector: AppStartupDataCollector,
    private val activityLoadEventEmitter: ActivityLifecycleListener?,
    private val drawEventEmitter: DrawEventEmitter?,
) : Application.ActivityLifecycleCallbacks {

    private var startupActivityId: Int? = null
    private var startupDataCollectionComplete = false

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            appStartupDataCollector.startupActivityPreCreated()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            appStartupDataCollector.startupActivityInitStart()
            val application = activity.application
            val callback = {
                appStartupDataCollector.firstFrameRendered(
                    activityName = activity.localClassName,
                    collectionCompleteCallback = { startupComplete(application) }
                )
            }
            drawEventEmitter?.registerFirstDrawCallback(activity, callback)
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            appStartupDataCollector.startupActivityPostCreated()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.isStartupActivity()) {
            appStartupDataCollector.startupActivityInitEnd()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeForStartup()) {
            val application = activity.application
            appStartupDataCollector.startupActivityResumed(
                activityName = activity.localClassName,
                collectionCompleteCallback = { startupComplete(application) }
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun startupComplete(application: Application) {
        if (!startupDataCollectionComplete) {
            application.unregisterActivityLifecycleCallbacks(this)
            if (activityLoadEventEmitter != null) {
                application.registerActivityLifecycleCallbacks(activityLoadEventEmitter)
            }
            startupDataCollectionComplete = true
        }
    }

    /**
     * Returns true if the Activity instance is being used as the startup Activity. It will return false if [useAsStartupActivity] has
     * not been called previously to setup the Activity instance to be used as the startup Activity.
     */
    private fun Activity.isStartupActivity(): Boolean {
        return if (observeForStartup()) {
            startupActivityId == traceInstanceId(this)
        } else {
            false
        }
    }

    /**
     * Use this Activity instance as the startup activity if appropriate. Return true the current instance is the startup Activity
     * instance going forward, false otherwise.
     */
    private fun Activity.useAsStartupActivity(): Boolean {
        if (isStartupActivity()) {
            return true
        }

        if (observeForStartup()) {
            startupActivityId = traceInstanceId(this)
        }

        return isStartupActivity()
    }

    private companion object {
        fun Activity.observeForStartup(): Boolean = !javaClass.isAnnotationPresent(StartupActivity::class.java)
    }
}
