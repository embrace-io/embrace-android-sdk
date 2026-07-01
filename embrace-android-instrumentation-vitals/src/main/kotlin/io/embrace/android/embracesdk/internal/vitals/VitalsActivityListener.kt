package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Window
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.WeakHashMap

/**
 * Installs the vitals listeners on each [Activity]'s [Window]: the touch callback
 * ([VitalsWindowCallback]) once per window, and the frame-metrics listener on resume, removed on
 * pause. Only instantiated on API 24+.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class VitalsActivityListener(
    private val logger: InternalLogger,
    private val focalCallbacks: FocalInteractionCallbacks,
    private val frameMetricsHandler: Handler,
    private val frameMetricsStrategy: FrameMetricsStrategy,
) : ActivityLifecycleCallbacks {

    private val frameListeners = WeakHashMap<Window, VitalsFrameMetricsListener>()

    override fun onActivityResumed(activity: Activity) {
        try {
            val window = activity.window ?: return
            installInteractionCallback(window)
            installFrameMetricsListener(window)
            focalCallbacks.onScreenStart()
        } catch (e: Throwable) {
            logger.logError("Failed to register vitals listeners", e)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        try {
            // Close any in-flight focal moment.
            focalCallbacks.onScreenStop()
            activity.window?.let(::removeFrameMetricsListener)
        } catch (e: Throwable) {
            logger.logError("Failed to unregister vitals listeners", e)
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

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
