package io.embrace.android.embracesdk.compose.internal

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledExecutorService

/**
 *  EmbraceGestureListener extends SimpleOnGestureListener to listen
 *  just for onSingleTapUp when the onClick event is triggered
 */
internal class EmbraceGestureListener(
    activity: Activity,
    private val onSingleTapUpBackgroundWorker: ScheduledExecutorService,
) : GestureDetector.SimpleOnGestureListener() {

    private var singleTapUpError: ComposeInternalErrorLogger = ComposeInternalErrorLogger()
    private var activityRef: WeakReference<Activity>

    private val composeClickedTargetIterator = ComposeClickedTargetIterator()

    init {
        activityRef = WeakReference(activity)
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        try {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                activityRef.get()?.let { activity ->
                    activity.window?.let {
                        logTapUp(it.decorView, event)
                    }
                }
            }
        } catch (e: Throwable) {
            singleTapUpError.logError(e)
        }

        return false
    }

    private fun logTapUp(decorView: View, event: MotionEvent) {
        composeClickedTargetIterator.findTarget(decorView, event.x, event.y, onSingleTapUpBackgroundWorker)
    }
}
