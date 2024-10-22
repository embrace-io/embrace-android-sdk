package io.embrace.android.embracesdk.compose.internal

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window

/**
 * Custom Window callback that triggers onTouch event
 * when dispatchTouchEvent happens
 */
internal class EmbraceWindowCallback(
    private val delegate: Window.Callback,
    private val gestureDetector: GestureDetector
) :
    Window.Callback by delegate {

    private val composeInternalErrorLogger = ComposeInternalErrorLogger()

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            try {
                val copy = MotionEvent.obtain(it)
                gestureDetector.onTouchEvent(copy)
                copy.recycle()
            } catch (e: Throwable) {
                composeInternalErrorLogger.logError(e)
            }
        }

        return delegate.dispatchTouchEvent(event)
    }
}
