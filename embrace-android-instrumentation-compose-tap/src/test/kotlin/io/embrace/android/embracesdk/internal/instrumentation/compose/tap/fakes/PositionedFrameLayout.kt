package io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes

import android.content.Context
import android.widget.FrameLayout

internal class PositionedFrameLayout(
    context: Context,
    private val winX: Int,
    private val winY: Int,
    width: Int,
    height: Int,
) : FrameLayout(context) {

    init {
        layout(0, 0, width, height)
    }

    override fun getLocationInWindow(outLocation: IntArray) {
        outLocation[0] = winX
        outLocation[1] = winY
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // no-op so children keep the bounds they were created with
    }
}
