@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.session.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.InternalErrorType
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.utils.ThreadUtils
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class EmbraceProcessStateService(
    private val clock: Clock,
    private val logger: EmbLogger
) : ProcessStateService {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners = CopyOnWriteArrayList<ProcessStateListener>()

    private var sessionOrchestrator: SessionOrchestrator? = null

    /**
     * States if the foreground phase comes from a cold start or not.
     */
    @Volatile
    private var coldStart = true

    /**
     * Returns if the app's in background or not.
     */
    @Volatile
    // TODO: future: investigate setting via ProcessLifecycleOwner initial state
    override var isInBackground = true
        private set

    init {
        // add lifecycle observer on main thread to avoid IllegalStateExceptions with
        // androidx.lifecycle
        ThreadUtils.runOnMainThread(
            logger,
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
        logger.logDebug("AppState: App entered foreground.")

        if (!isInBackground) {
            val msg = "Unbalanced call to onForeground(). This will contribute to session loss."
            logger.logError(msg, InternalError(msg))
        }
        isInBackground = false
        val timestamp = clock.now()

        invokeCallbackSafely { sessionOrchestrator?.onForeground(coldStart, timestamp) }

        stream<ProcessStateListener>(listeners) { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onForeground(coldStart, timestamp)
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
        logger.logDebug("AppState: App entered background")
        isInBackground = true
        val timestamp = clock.now()
        invokeCallbackSafely { sessionOrchestrator?.onBackground(timestamp) }

        stream<ProcessStateListener>(listeners) { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onBackground(timestamp)
            }
        }
    }

    private inline fun invokeCallbackSafely(action: () -> Unit) {
        try {
            action()
        } catch (ex: Exception) {
            logger.logWarning(ERROR_FAILED_TO_NOTIFY)
            logger.trackInternalError(InternalErrorType.PROCESS_STATE_CALLBACK_FAIL, ex)
        }
    }

    override fun addListener(listener: ProcessStateListener) {
        when (listener) {
            is SessionOrchestrator -> sessionOrchestrator = listener
            else -> listeners.addIfAbsent(listener)
        }
    }

    override fun close() {
        try {
            logger.logDebug("Shutting down EmbraceProcessStateService")
            listeners.clear()
            sessionOrchestrator = null
        } catch (ex: Exception) {
            logger.logWarning("Error when closing EmbraceProcessStateService", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify EmbraceProcessStateService listener"
    }
}
