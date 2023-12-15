package io.embrace.android.embracesdk.internal

import android.graphics.Canvas
import android.view.View
import io.embrace.android.embracesdk.EmbraceContext

public class MockView(public val context: EmbraceContext) : View(context) {

    init {
        right = context.screenshotBitmap.width
        left = 0
        top = 0
        bottom = context.screenshotBitmap.height
    }

    @SuppressWarnings("MissingSuperCall")
    override fun draw(canvas: Canvas?) {
        canvas?.setBitmap(context.screenshotBitmap)
    }
}
