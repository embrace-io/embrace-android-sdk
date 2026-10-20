package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.view.GestureDetector
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class ComposeActivityListener(
    private val errorHandler: ErrorHandler,
    private val dataSource: ComposeTapDataSource,
) : ActivityLifecycleCallbacks, AutoCloseable {

    /**
     * Windows that already have an [EmbraceWindowCallback] installed so that we don't attempt to double-install our `Callback` delegate.
     * The callback is weakly referenced because it strongly references its window via the delegate, which would stop the
     * window key from ever being collected.
     */
    private val windowCallbacks = WeakHashMap<Window, WeakReference<EmbraceWindowCallback>>()

    private var closed = false

    override fun onActivityResumed(activity: Activity) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            synchronized(windowCallbacks) {
                if (closed || windowCallbacks.containsKey(window)) {
                    return@guardSdkCode
                }
                val gestureListener = EmbraceGestureListener(activity, errorHandler, dataSource)
                val gestureDetectorCompat = GestureDetector(activity, gestureListener)
                val callback = EmbraceWindowCallback(
                    window.callback,
                    gestureDetectorCompat,
                    errorHandler,
                )
                windowCallbacks[window] = WeakReference(callback)
                window.callback = callback
            }
        }
    }

    override fun close() {
        synchronized(windowCallbacks) {
            closed = true
            windowCallbacks.values.forEach { it.get()?.close() }
            windowCallbacks.clear()
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }
}
