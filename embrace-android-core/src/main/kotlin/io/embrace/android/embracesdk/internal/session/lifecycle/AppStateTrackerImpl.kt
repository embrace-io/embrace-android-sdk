package io.embrace.android.embracesdk.internal.session.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class AppStateTrackerImpl(
    private val clock: Clock,
    private val logger: EmbLogger,
    private val lifecycleOwner: LifecycleOwner,
) : AppStateTracker, LifecycleEventObserver {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners: CopyOnWriteArrayList<AppStateListener> = CopyOnWriteArrayList<AppStateListener>()

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
    private var state: AppState = when {
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> AppState.FOREGROUND
        else -> AppState.BACKGROUND
    }

    private val mainLooper = Looper.getMainLooper()
    private val mainThread = mainLooper.thread

    init {
        // add lifecycle observer on main thread to avoid IllegalStateExceptions with
        // androidx.lifecycle
        val wrappedRunnable = Runnable {
            runCatching {
                lifecycleOwner.lifecycle.addObserver(this)
            }
        }
        if (Thread.currentThread() !== mainThread) {
            val mainHandler = Handler(mainLooper)
            mainHandler.post(wrappedRunnable)
        } else {
            wrappedRunnable.run()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> onForeground()
            Lifecycle.Event.ON_STOP -> onBackground()
            else -> {
                // no-op
            }
        }
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON START.
     */
    internal fun onForeground() {
        state = AppState.FOREGROUND
        val timestamp = clock.now()

        invokeCallbackSafely { sessionOrchestrator?.onForeground(coldStart, timestamp) }

        listeners.toList().forEach { listener: AppStateListener ->
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
    internal fun onBackground() {
        state = AppState.BACKGROUND
        val timestamp = clock.now()

        listeners.toList().forEach { listener: AppStateListener ->
            invokeCallbackSafely {
                listener.onBackground(timestamp)
            }
        }

        invokeCallbackSafely { sessionOrchestrator?.onBackground(timestamp) }
    }

    private inline fun invokeCallbackSafely(action: () -> Unit) {
        try {
            action()
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.APP_STATE_CALLBACK_FAIL, ex)
        }
    }

    override fun addListener(listener: AppStateListener) {
        when (listener) {
            is SessionOrchestrator -> sessionOrchestrator = listener
            else -> listeners.addIfAbsent(listener)
        }
    }

    override fun getAppState(): AppState = state
}
