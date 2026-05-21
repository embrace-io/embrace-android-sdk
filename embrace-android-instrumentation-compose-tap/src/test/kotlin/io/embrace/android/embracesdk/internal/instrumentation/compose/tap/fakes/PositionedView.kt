package io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes

import android.content.Context
import android.view.View

/**
 * A [View] that reports a fixed window position and counts how many times the iterator queried it.
 *
 * The counter lets tests place this view inside a parent and use the count as a sentinel for
 * whether the iterator descended into that parent.
 */
internal class PositionedView(
    context: Context,
    private val winX: Int,
    private val winY: Int,
    width: Int,
    height: Int,
) : View(context) {

    var locationCheckCount: Int = 0
        private set

    init {
        layout(0, 0, width, height)
    }

    override fun getLocationInWindow(outLocation: IntArray) {
        locationCheckCount++
        outLocation[0] = winX
        outLocation[1] = winY
    }
}
