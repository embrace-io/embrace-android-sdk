package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.ArrayDeque

internal class ComposeClickedTargetIterator(
    private val logger: InternalLogger,
    dataSource: ComposeTapDataSource,
) : EmbraceClickedTargetIterator {

    private val nodeLocator = EmbraceNodeIterator(dataSource)
    private val windowLocation = IntArray(2)

    override fun findTarget(
        decorView: View,
        x: Float,
        y: Float,
    ) {
        try {
            if (!containsWindowPoint(decorView, x, y)) {
                return
            }

            val queue = ArrayDeque<View>()
            queue.add(decorView)
            while (queue.isNotEmpty()) {
                val view = queue.removeFirst()
                if (view is ViewGroup) {
                    // scan the children in reverse (z-index) order
                    for (i in view.childCount - 1 downTo 0) {
                        val child = view.getChildAt(i)
                        // ViewGroups that don't clipChildren need to be fully scanned
                        if (!view.clipChildren || containsWindowPoint(child, x, y)) {
                            queue.add(child)
                        }
                    }
                }
                if (view.parent is ComposeView) {
                    nodeLocator.findClickedElement(view, x, y)
                }
            }
        } catch (e: Throwable) {
            logger.logError("Failed to find target", e)
        }
    }

    private fun containsWindowPoint(view: View, x: Float, y: Float): Boolean {
        @SuppressLint("UseKtx") // core-ktx is not a dependency of this module
        if (view.visibility != View.VISIBLE) {
            return false
        }

        view.getLocationInWindow(windowLocation)
        val (viewX, viewY) = windowLocation
        return x >= viewX && x < viewX + view.width &&
            y >= viewY && y < viewY + view.height
    }
}
