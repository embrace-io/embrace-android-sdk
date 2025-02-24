package io.embrace.android.embracesdk.compose.internal

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference

/**
 *  EmbraceGestureListener extends SimpleOnGestureListener to listen
 *  just for onSingleTapUp when the onClick event is triggered
 */
internal class EmbraceGestureListener(
    activity: Activity,
) : GestureDetector.SimpleOnGestureListener() {

    private val singleTapUpError: ComposeInternalErrorLogger = ComposeInternalErrorLogger()
    private val activityRef: WeakReference<Activity> = WeakReference(activity)
    private val composeClickedTargetIterator = ComposeClickedTargetIterator()

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
        composeClickedTargetIterator.findTarget(decorView, event.x, event.y)
    }
}
