package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.view.GestureDetector
import android.view.Window
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class ComposeActivityListener(
    private val logger: InternalLogger,
    private val dataSource: ComposeTapDataSource,
) : ActivityLifecycleCallbacks, Closeable {

    /**
     * Windows that already have an [EmbraceWindowCallback] installed so that we don't attempt to double-install our `Callback` delegate.
     * The callback is weakly referenced because it strongly references its window via the delegate, which would stop the
     * window key from ever being collected.
     */
    private val windowCallbacks = WeakHashMap<Window, WeakReference<EmbraceWindowCallback>>()

    private var closed = false

    override fun onActivityResumed(activity: Activity) {
        try {
            // Set EmbraceWindowCallback to install Embrace Gesture Listener to capture onClick events
            val window: Window = activity.window
            synchronized(windowCallbacks) {
                if (closed || windowCallbacks.containsKey(window)) {
                    return
                }
                val gestureListener = EmbraceGestureListener(activity, logger, dataSource)
                val gestureDetectorCompat = GestureDetector(activity, gestureListener)
                val callback = EmbraceWindowCallback(
                    window.callback,
                    gestureDetectorCompat,
                    logger,
                )
                windowCallbacks[window] = WeakReference(callback)
                window.callback = callback
            }
        } catch (e: Throwable) {
            logger.logError("Failed to register gesture listener", e)
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
