package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import java.lang.ref.WeakReference

/**
 *  EmbraceGestureListener extends SimpleOnGestureListener to listen
 *  just for onSingleTapUp when the onClick event is triggered
 */
internal class EmbraceGestureListener(
    activity: Activity,
    private val errorHandler: ErrorHandler,
    dataSource: ComposeTapDataSource,
) : GestureDetector.SimpleOnGestureListener() {

    private val activityRef: WeakReference<Activity> = WeakReference(activity)
    private val composeClickedTargetIterator = ComposeClickedTargetIterator(errorHandler, dataSource)

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        try {
            activityRef.get()?.let { activity ->
                activity.window?.let {
                    logTapUp(it.decorView, event)
                }
            }
        } catch (e: Throwable) {
            errorHandler.log("Failed to process singleTapUp", e, severity = ErrorHandler.Severity.ERROR)
        }
        return false
    }

    private fun logTapUp(decorView: View, event: MotionEvent) {
        composeClickedTargetIterator.findTarget(decorView, event.x, event.y)
    }
}
