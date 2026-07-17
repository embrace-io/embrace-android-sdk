package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Window
import androidx.annotation.RequiresApi
import java.util.WeakHashMap

/**
 * Installs the vitals listeners on each [Activity]'s [Window]: the touch callback
 * ([VitalsWindowCallback]) once per window, and the frame-metrics listener on resume, removed on
 * pause. Only instantiated on API 24+.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class VitalsActivityListener(
    private val focalCallbacks: FocalInteractionCallbacks,
    private val navSource: ActivityNavigationSource,
    private val frameMetricsHandler: Handler,
    private val frameMetricsStrategy: FrameMetricsStrategy,
) : ActivityLifecycleCallbacks {

    private val frameListeners = WeakHashMap<Window, VitalsFrameMetricsListener>()

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        // captured first so the navigation-start timestamp reflects this event, not the delay until it's processed
        val eventTime = SystemClock.uptimeMillis()
        try {
            // a non-null bundle means the Activity is being recreated (config change / restore), not navigated to
            navSource.onActivityCreated(activity.localClassName, recreated = bundle != null, eventTime = eventTime)
        } catch (_: Throwable) {
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // captured first so the navigation-end timestamp reflects this event, not the delay until it's processed
        val eventTime = SystemClock.uptimeMillis()
        try {
            val window = activity.window ?: return
            installInteractionCallback(window)
            installFrameMetricsListener(window)
            focalCallbacks.onScreenStart()
            // a resumed Activity is the navigation end onto its screen
            navSource.onActivityResumed(activity.localClassName, eventTime)
        } catch (_: Throwable) {
        }
    }

    override fun onActivityPaused(activity: Activity) {
        try {
            // Close any in-flight focal moment.
            focalCallbacks.onScreenStop()
            activity.window?.let(::removeFrameMetricsListener)
        } catch (_: Throwable) {
        }
    }

    private fun installInteractionCallback(window: Window) {
        if (window.callback !is VitalsWindowCallback) {
            window.callback = VitalsWindowCallback(
                delegate = window.callback,
                focalCallbacks = focalCallbacks,
            )
        }
    }

    private fun installFrameMetricsListener(window: Window) {
        if (frameListeners.containsKey(window)) {
            return
        }
        val listener = VitalsFrameMetricsListener(focalCallbacks, frameMetricsStrategy)
        frameListeners[window] = listener
        window.addOnFrameMetricsAvailableListener(listener, frameMetricsHandler)
    }

    private fun removeFrameMetricsListener(window: Window) {
        frameListeners.remove(window)?.let { listener ->
            window.removeOnFrameMetricsAvailableListener(listener)
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
