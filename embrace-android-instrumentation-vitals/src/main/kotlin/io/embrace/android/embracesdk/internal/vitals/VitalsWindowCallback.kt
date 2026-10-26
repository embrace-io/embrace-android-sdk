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
    focalCallbacks: FocalInteractionCallbacks,
    errors: OnceUiCallbackErrorTracker,
) : Window.Callback by delegate, AutoCloseable {

    // nulled on close so that a window outliving the SDK does not retain it
    @Volatile
    private var focalCallbacks: FocalInteractionCallbacks? = focalCallbacks

    @Volatile
    private var errors: OnceUiCallbackErrorTracker? = errors

    override fun close() {
        focalCallbacks = null
        errors = null
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val focalCallbacks = focalCallbacks ?: return delegate.dispatchTouchEvent(event)
        try {
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> focalCallbacks.onInteractionStart()
                MotionEvent.ACTION_MOVE -> focalCallbacks.onInteractionMove()
                MotionEvent.ACTION_UP -> {
                    focalCallbacks.onInteractionEnd()

                    // a completed tap (not a cancel) is the action that may trigger a navigation
                    focalCallbacks.onTap(event.eventTime)
                }

                MotionEvent.ACTION_CANCEL -> focalCallbacks.onInteractionEnd()
            }
        } catch (exc: Throwable) {
            errors?.track(exc)
        }

        return delegate.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        val focalCallbacks = focalCallbacks ?: return delegate.onWindowFocusChanged(hasFocus)
        try {
            // gaining focus marks the end of the open transition; extends an in-flight screen load past the animation tail
            if (hasFocus) {
                focalCallbacks.onWindowFocused()
            }
        } catch (exc: Throwable) {
            errors?.track(exc)
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
