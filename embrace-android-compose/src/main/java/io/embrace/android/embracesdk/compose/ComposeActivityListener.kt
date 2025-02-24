package io.embrace.android.embracesdk.compose

import android.app.Activity
import android.view.GestureDetector
import android.view.Window
import io.embrace.android.embracesdk.compose.internal.ComposeInternalErrorLogger
import io.embrace.android.embracesdk.compose.internal.EmbraceGestureListener
import io.embrace.android.embracesdk.compose.internal.EmbraceWindowCallback

class ComposeActivityListener : ActivityLifeCycleCallbacks {

    private val composeInternalErrorLogger = ComposeInternalErrorLogger()

    override fun onActivityResumed(activity: Activity) {
        try {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            if (window.callback == null || window.callback !is EmbraceWindowCallback) {
                val gestureDetectorCompat = GestureDetector(activity, EmbraceGestureListener(activity))
                window.callback = EmbraceWindowCallback(
                    window.callback,
                    gestureDetectorCompat
                )
            }
        } catch (e: Throwable) {
            composeInternalErrorLogger.logError(e)
        }
    }
}
