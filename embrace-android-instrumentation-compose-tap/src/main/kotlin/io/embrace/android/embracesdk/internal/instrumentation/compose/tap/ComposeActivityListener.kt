package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.view.GestureDetector
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.WeakHashMap

internal class ComposeActivityListener(
    private val logger: InternalLogger,
    private val dataSource: ComposeTapDataSource,
) : ActivityLifecycleCallbacks {

    private val windowCallbacks = WeakHashMap<Window, EmbraceWindowCallback>()

    override fun onActivityResumed(activity: Activity) {
        try {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            if (!windowCallbacks.containsKey(window)) {
                val gestureListener = EmbraceGestureListener(activity, logger, dataSource)
                val gestureDetectorCompat = GestureDetector(activity, gestureListener)
                val callback = EmbraceWindowCallback(
                    window.callback,
                    gestureDetectorCompat,
                    logger,
                )
                window.callback = callback
                windowCallbacks[window] = callback
            }
        } catch (e: Throwable) {
            logger.logError("Failed to register gesture listener", e)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }
}
