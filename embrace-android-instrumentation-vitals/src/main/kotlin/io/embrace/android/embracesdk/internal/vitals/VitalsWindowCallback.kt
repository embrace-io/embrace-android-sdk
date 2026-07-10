package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window

/**
 * Wraps a [Window.Callback] to report the signals captured from touch dispatch and window focus.
 * Delegates every call to the original callback.
 */
internal class VitalsWindowCallback(
    private val delegate: Window.Callback,
    private val focalCallbacks: FocalInteractionCallbacks,
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        try {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> focalCallbacks.onInteractionStart(event.eventTime)
                MotionEvent.ACTION_MOVE -> focalCallbacks.onInteractionMove()
                MotionEvent.ACTION_UP -> {
                    focalCallbacks.onInteractionEnd()

                    // a completed tap (not a cancel) is the action that may trigger a navigation
                    focalCallbacks.onTap(event.eventTime)
                }

                MotionEvent.ACTION_CANCEL -> focalCallbacks.onInteractionEnd()
            }
        } catch (_: Throwable) {
        }

        return delegate.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        try {
            // gaining focus marks the end of the open transition; extends an in-flight screen load past the animation tail
            if (hasFocus) {
                focalCallbacks.onWindowFocused()
            }
        } catch (_: Throwable) {
        }

        delegate.onWindowFocusChanged(hasFocus)
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
