package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import java.lang.ref.WeakReference

/**
 *  EmbraceGestureListener extends SimpleOnGestureListener to listen
 *  just for onSingleTapUp when the onClick event is triggered
 */
internal class EmbraceGestureListener(
    activity: Activity,
    private val logger: EmbLogger,
    dataSource: ComposeTapDataSource,
) : GestureDetector.SimpleOnGestureListener() {

    private val activityRef: WeakReference<Activity> = WeakReference(activity)
    private val composeClickedTargetIterator = ComposeClickedTargetIterator(logger, dataSource)

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
            logger.logError("Failed to process singleTapUp", e)
        }
        return false
    }

    private fun logTapUp(decorView: View, event: MotionEvent) {
        composeClickedTargetIterator.findTarget(decorView, event.x, event.y)
    }
}
