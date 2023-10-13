package io.embrace.android.embracesdk.compose.internal

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ScheduledExecutorService

internal class ComposeClickedTargetIterator : EmbraceClickedTargetIterator {

    private val composeInternalErrorLogger = ComposeInternalErrorLogger()
    private val nodeLocator = EmbraceNodeIterator()

    override fun findTarget(decorView: View, x: Float, y: Float, onSingleTapUpBackgroundWorker: ScheduledExecutorService) {
        try {
            val queue: Queue<View> = LinkedList()
            queue.add(decorView)
            while (queue.size > 0) {
                val view = queue.poll()
                view?.let {
                    if (it is ViewGroup) {
                        // TODO: define a limit of how many views we want to store and process to avoid processing ridiculously large view
                        for (i in 0 until it.childCount) {
                            queue.add(it.getChildAt(i))
                        }
                    }

                    if (it.parent is ComposeView) { // this validation is to reduce the locate method execution to the proper view
                        nodeLocator.findClickedElement(it, x, y, onSingleTapUpBackgroundWorker)
                    }
                }
            }
        } catch (e: Throwable) {
            composeInternalErrorLogger.logError(e)
        }
    }
}
