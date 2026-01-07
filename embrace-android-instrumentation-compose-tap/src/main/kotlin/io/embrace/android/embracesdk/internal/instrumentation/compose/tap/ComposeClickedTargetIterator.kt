package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.LinkedList
import java.util.Queue

internal class ComposeClickedTargetIterator(
    private val logger: InternalLogger,
    dataSource: ComposeTapDataSource,
) : EmbraceClickedTargetIterator {

    private val nodeLocator = EmbraceNodeIterator(dataSource)

    override fun findTarget(
        decorView: View,
        x: Float,
        y: Float,
    ) {
        try {
            val queue: Queue<View> = LinkedList()
            queue.add(decorView)
            while (queue.isNotEmpty()) {
                val view = queue.poll()
                view?.let {
                    if (it is ViewGroup) {
                        // TODO: define a limit of how many views we want to store and process to avoid processing ridiculously large view
                        for (i in 0 until it.childCount) {
                            queue.add(it.getChildAt(i))
                        }
                    }

                    if (it.parent is ComposeView) { // this validation is to reduce the locate method execution to the proper view
                        nodeLocator.findClickedElement(it, x, y)
                    }
                }
            }
        } catch (e: Throwable) {
            logger.logError("Failed to find target", e)
        }
    }
}
