package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Window
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Installs the vitals listeners on each [Activity]'s [Window]: the touch callback
 * ([VitalsWindowCallback]) once per window, and the frame-metrics listener on resume, removed on
 * pause. Only instantiated on API 24+.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class VitalsActivityListener(
    private val focalCallbacks: FocalInteractionCallbacks,
    private val navSource: ActivityNavigationSource,
    private val frameMetricsHandler: Handler,
    private val frameMetricsStrategy: FrameMetricsStrategy,
    private val eventBus: EventBus,
    errorHandler: ErrorHandler,
) : AutoCloseable {

    private val activityErrors = OnceUiCallbackErrorTracker(errorHandler)
    private val frameErrors = OnceUiCallbackErrorTracker(errorHandler)
    private val windowErrors = OnceUiCallbackErrorTracker(errorHandler)

    private val frameListeners = WeakHashMap<Window, VitalsFrameMetricsListener>()

    @Volatile
    private var closed = false

    /**
     * Windows that already have a [VitalsWindowCallback] installed so that we don't attempt to double-install our `Callback` delegate.
     * The callback is weakly referenced because it strongly references its window via the delegate, which would stop the
     * window key from ever being collected.
     */
    private val interactionCallbacks = WeakHashMap<Window, WeakReference<VitalsWindowCallback>>()

    private val onCreated = EventHandler<ActivityCreated> { event ->
        // captured first so the navigation-start timestamp reflects this event, not the delay until it's processed
        val eventTime = SystemClock.uptimeMillis()
        try {
            // a non-null saved state means the Activity is being recreated (config change / restore), not navigated to
            val recreated = event.savedInstanceState != null
            navSource.onActivityCreated(event.activity.localClassName, recreated = recreated, eventTime = eventTime)
        } catch (exc: Throwable) {
            activityErrors.track(exc)
        }
    }

    private val onResumed = EventHandler<Activity> { activity ->
        // captured first so the navigation-end timestamp reflects this event, not the delay until it's processed
        val eventTime = SystemClock.uptimeMillis()
        try {
            val window = activity.window ?: return@EventHandler
            installInteractionCallback(window)
            installFrameMetricsListener(window)
            focalCallbacks.onScreenStart()
            // a resumed Activity is the navigation end onto its screen
            navSource.onActivityResumed(activity.localClassName, eventTime)
        } catch (exc: Throwable) {
            activityErrors.track(exc)
        }
    }

    private val onPaused = EventHandler<Activity> { activity ->
        try {
            // Close any in-flight focal moment.
            focalCallbacks.onScreenStop()
            activity.window?.let(::removeFrameMetricsListener)
        } catch (exc: Throwable) {
            activityErrors.track(exc)
        }
    }

    /**
     * Starts handling the created, resumed and paused events of every Activity.
     */
    fun subscribe() {
        eventBus.addHandler(ActivityLifecycle.CREATED, onCreated)
        eventBus.addHandler(ActivityLifecycle.RESUMED, onResumed)
        eventBus.addHandler(ActivityLifecycle.PAUSED, onPaused)
    }

    private fun installInteractionCallback(window: Window) {
        synchronized(interactionCallbacks) {
            if (closed || interactionCallbacks.containsKey(window)) {
                return
            }
            val callback = VitalsWindowCallback(
                delegate = window.callback,
                focalCallbacks = focalCallbacks,
                errors = windowErrors,
            )
            interactionCallbacks[window] = WeakReference(callback)
            window.callback = callback
        }
    }

    private fun installFrameMetricsListener(window: Window) {
        synchronized(frameListeners) {
            if (closed || frameListeners.containsKey(window)) {
                return
            }
            val listener = VitalsFrameMetricsListener(focalCallbacks, frameMetricsStrategy, frameErrors)
            frameListeners[window] = listener
            window.addOnFrameMetricsAvailableListener(listener, frameMetricsHandler)
        }
    }

    private fun removeFrameMetricsListener(window: Window) {
        synchronized(frameListeners) {
            frameListeners.remove(window)?.let { listener ->
                window.removeOnFrameMetricsAvailableListener(listener)
            }
        }
    }

    /**
     * Stops handling Activity events, removes the frame-metrics listeners from every window that still has one, and closes
     * every installed [VitalsWindowCallback] so it only calls through to the original callback.
     */
    override fun close() {
        eventBus.removeHandler(ActivityLifecycle.CREATED, onCreated)
        eventBus.removeHandler(ActivityLifecycle.RESUMED, onResumed)
        eventBus.removeHandler(ActivityLifecycle.PAUSED, onPaused)
        val installed = synchronized(frameListeners) {
            closed = true
            frameListeners.toMap().also { frameListeners.clear() }
        }
        val remove = Runnable {
            installed.forEach { (window, listener) ->
                runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            }
        }
        val mainLooper = Looper.getMainLooper()
        if (Looper.myLooper() == mainLooper) {
            remove.run()
        } else {
            Handler(mainLooper).post(remove)
        }
        synchronized(interactionCallbacks) {
            interactionCallbacks.values.forEach { it.get()?.close() }
            interactionCallbacks.clear()
        }
    }
}
