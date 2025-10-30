package io.embrace.android.embracesdk.compose.internal

import android.os.Build
import android.view.GestureDetector
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Custom Window callback that triggers onTouch event
 * when dispatchTouchEvent happens
 */
internal class EmbraceWindowCallback(
    private val delegate: Window.Callback,
    private val gestureDetector: GestureDetector,
    private val logger: EmbLogger,
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            try {
                val copy = MotionEvent.obtain(it)
                gestureDetector.onTouchEvent(copy)
                copy.recycle()
            } catch (e: Throwable) {
                logger.logError("Touch event dispatch failed", e)
            }
        }

        return delegate.dispatchTouchEvent(event)
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            delegate.onPointerCaptureChanged(hasCapture)
        }
    }

    override fun onProvideKeyboardShortcuts(
        data: List<KeyboardShortcutGroup?>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            delegate.onProvideKeyboardShortcuts(data, menu, deviceId)
        }
    }
}
