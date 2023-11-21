package io.embrace.android.embracesdk.session.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.utils.ThreadUtils
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class EmbraceProcessStateService(
    private val clock: Clock,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : ProcessStateService {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners = CopyOnWriteArrayList<ProcessStateListener>()

    private var sessionService: SessionService? = null
    private var backgroundActivityService: BackgroundActivityService? = null

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

        if (!isInBackground) {
            val msg = "Unbalanced call to onForeground(). This will contribute to session loss."
            logger.logError(msg, InternalError(msg))
        }

        isInBackground = false
        val timestamp = clock.now()

        invokeCallbackSafely { backgroundActivityService?.onForeground(coldStart, startTime, timestamp) }
        invokeCallbackSafely { sessionService?.onForeground(coldStart, startTime, timestamp) }

        stream<ProcessStateListener>(listeners) { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onForeground(coldStart, startTime, timestamp)
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
        invokeCallbackSafely { sessionService?.onBackground(timestamp) }
        invokeCallbackSafely { backgroundActivityService?.onBackground(timestamp) }

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
            logDebug(ERROR_FAILED_TO_NOTIFY, ex)
        }
    }

    override fun addListener(listener: ProcessStateListener) {
        when (listener) {
            is SessionService -> sessionService = listener
            is BackgroundActivityService -> backgroundActivityService = listener
            else -> listeners.addIfAbsent(listener)
        }
    }

    override fun close() {
        try {
            logDebug("Shutting down EmbraceProcessStateService")
            listeners.clear()
            backgroundActivityService = null
            sessionService = null
        } catch (ex: Exception) {
            logDebug("Error when closing EmbraceProcessStateService", ex)
        }
    }

    companion object {
        private const val ERROR_FAILED_TO_NOTIFY =
            "Failed to notify EmbraceProcessStateService listener"
    }
}
