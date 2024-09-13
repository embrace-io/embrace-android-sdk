package io.embrace.android.embracesdk.compose

import android.app.Activity
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import io.embrace.android.embracesdk.compose.internal.ComposeInternalErrorLogger
import io.embrace.android.embracesdk.compose.internal.EmbraceGestureListener
import io.embrace.android.embracesdk.compose.internal.EmbraceWindowCallback
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

class ComposeActivityListener : ActivityLifeCycleCallbacks {

    private val threadFactory: ThreadFactory = ThreadFactory { runnable: Runnable ->
        Executors.defaultThreadFactory().newThread(runnable).apply {
            this.name = "emb-compose-scheduled-reg"
        }
    }

    private val service: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(threadFactory)

    private val composeInternalErrorLogger = ComposeInternalErrorLogger()

    override fun onActivityResumed(activity: Activity) {
        try {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            if (window.callback == null || window.callback !is EmbraceWindowCallback) {
                val gestureDetectorCompat = GestureDetectorCompat(activity, EmbraceGestureListener(activity, service))
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
