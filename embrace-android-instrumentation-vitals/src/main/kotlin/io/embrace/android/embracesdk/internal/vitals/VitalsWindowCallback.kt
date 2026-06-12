package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.InternalLogger

/**
 * Wraps a [Window.Callback] to report interactions from touch dispatch: a touch-down starts an
 * interaction, a touch-move is a liveness signal, and a touch-up/cancel ends it. Delegates every call
 * to the original callback.
 */
internal class VitalsWindowCallback(
    private val delegate: Window.Callback,
    private val logger: InternalLogger,
    private val focalCallbacks: FocalInteractionCallbacks,
) : Window.Callback by delegate {

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        try {
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> focalCallbacks.onInteractionStart()
                MotionEvent.ACTION_MOVE -> focalCallbacks.onInteractionMove()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> focalCallbacks.onInteractionEnd()
            }
        } catch (e: Throwable) {
            logger.logError("Vitals touch event dispatch failed", e)
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
