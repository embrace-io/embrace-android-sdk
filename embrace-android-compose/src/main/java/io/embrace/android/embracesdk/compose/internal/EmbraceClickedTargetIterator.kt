package io.embrace.android.embracesdk.compose.internal

import android.view.View
import java.util.concurrent.ScheduledExecutorService

/**
 * Given a view and a position ( x, y),
 * this interface is intended to represent
 * functionality that looks for a clicked view in that position.
 */
internal interface EmbraceClickedTargetIterator {
    fun findTarget(
        decorView: View,
        x: Float,
        y: Float,
        onSingleTapUpBackgroundWorker: ScheduledExecutorService,
    )
}
