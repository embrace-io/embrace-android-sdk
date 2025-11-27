package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.view.GestureDetector
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.EmbLogger

internal class ComposeActivityListener(
    private val logger: EmbLogger,
    private val dataSource: ComposeTapDataSource,
) : ActivityLifecycleCallbacks {

    override fun onActivityResumed(activity: Activity) {
        try {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            if (window.callback == null || window.callback !is EmbraceWindowCallback) {
                val gestureListener = EmbraceGestureListener(activity, logger, dataSource)
                val gestureDetectorCompat = GestureDetector(activity, gestureListener)
                window.callback = EmbraceWindowCallback(
                    window.callback,
                    gestureDetectorCompat,
                    logger,
                )
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
