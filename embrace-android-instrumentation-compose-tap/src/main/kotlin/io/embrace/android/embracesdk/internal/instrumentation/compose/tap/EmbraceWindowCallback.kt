package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.os.Build
import android.view.GestureDetector
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

/**
 * Custom Window callback that triggers onTouch event
 * when dispatchTouchEvent happens
 */
internal class EmbraceWindowCallback(
    private val delegate: Window.Callback,
    gestureDetector: GestureDetector,
    errorHandler: ErrorHandler,
) : Window.Callback by delegate, AutoCloseable {

    // nulled on close so that windows outliving the SDK do not retain it
    @Volatile
    private var tapCapture: TapCapture? = TapCapture(gestureDetector, errorHandler)

    override fun close() {
        tapCapture = null
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        val capture = tapCapture ?: return delegate.dispatchTouchEvent(event)
        event?.let {
            capture.errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
                val copy = MotionEvent.obtain(it)
                capture.gestureDetector.onTouchEvent(copy)
                copy.recycle()
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

    private class TapCapture(
        val gestureDetector: GestureDetector,
        val errorHandler: ErrorHandler,
    )
}
