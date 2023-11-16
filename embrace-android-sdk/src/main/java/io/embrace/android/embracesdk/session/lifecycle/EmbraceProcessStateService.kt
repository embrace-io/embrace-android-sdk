package io.embrace.android.embracesdk.session.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.utils.ThreadUtils
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class EmbraceProcessStateService(
    private val clock: Clock
) : ProcessStateService {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */

    val listeners = CopyOnWriteArrayList<ProcessStateListener>()

    /**
     * States if the foreground phase comes from a cold start or not.
     */
    @Volatile
    private var coldStart = true

    /**
     * States the initialization time of the EmbraceProcessStateService, inferring it is initialized
     * from the [Embrace.start] method.
     */
    private val startTime: Long = clock.now()

    /**
     * Returns if the app's in background or not.
     */
    @Volatile
    override var isInBackground = true
        private set

    init {
        // add lifecycle observer on main thread to avoid IllegalStateExceptions with
        // androidx.lifecycle
        ThreadUtils.runOnMainThread(
            Runnable {
                ProcessLifecycleOwner.get().lifecycle
                    .addObserver(this@EmbraceProcessStateService)
            }
        )
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON START.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    override fun onForeground() {
        logDebug("AppState: App entered foreground.")
        isInBackground = false
        val timestamp = clock.now()
        stream<ProcessStateListener>(listeners) { listener: ProcessStateListener ->
            try {
                listener.onForeground(coldStart, startTime, timestamp)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
        coldStart = false
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON STOP.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    override fun onBackground() {
        logDebug("AppState: App entered background")
        isInBackground = true
        val timestamp = clock.now()
        stream<ProcessStateListener>(listeners) { listener: ProcessStateListener ->
            try {
                InternalStaticEmbraceLogger.logger.logWarning("onBackground() listener: $listener")
                listener.onBackground(timestamp)
            } catch (ex: Exception) {
                logDebug(ERROR_FAILED_TO_NOTIFY, ex)
            }
        }
    }

    override fun addListener(listener: ProcessStateListener) {
        // assumption: we always need to run the Session service first, then everything else,
        // because otherwise the session envelope will not be created. The ActivityListener
        // could use separating from session handling, but that's a bigger change.
        val priority = listener is SessionService
        if (!listeners.contains(listener)) {
            if (priority) {
                listeners.add(0, listener)
            } else {
                listeners.addIfAbsent(listener)
            }
        }
    }

    override fun close() {
        try {
            logDebug("Shutting down EmbraceProcessStateService")
            listeners.clear()
        } catch (ex: Exception) {
            logDebug("Error when closing EmbraceProcessStateService", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify EmbraceProcessStateService listener"
    }
}
