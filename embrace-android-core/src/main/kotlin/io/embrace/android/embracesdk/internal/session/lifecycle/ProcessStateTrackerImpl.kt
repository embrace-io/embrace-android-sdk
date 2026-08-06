package io.embrace.android.embracesdk.internal.session.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class ProcessStateTrackerImpl(
    private val logger: InternalLogger,
    private val lifecycleOwner: LifecycleOwner,
) : ProcessStateTracker, LifecycleEventObserver {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners: CopyOnWriteArrayList<ProcessStateListener> = CopyOnWriteArrayList<ProcessStateListener>()

    private var sessionOrchestrator: SessionOrchestrator? = null

    /**
     * Returns if the app's in background or not.
     */
    @Volatile
    private var state: ProcessState = when {
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> ProcessState.FOREGROUND
        else -> ProcessState.BACKGROUND
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
        state = ProcessState.FOREGROUND

        invokeCallbackSafely { sessionOrchestrator?.onForeground() }

        listeners.toList().forEach { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onForeground()
            }
        }
    }

    /**
     * This method will be called by the ProcessLifecycleOwner when the main app process calls
     * ON STOP.
     */
    internal fun onBackground() {
        state = ProcessState.BACKGROUND

        listeners.toList().forEach { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onBackground()
            }
        }

        invokeCallbackSafely { sessionOrchestrator?.onBackground() }
    }

    private inline fun invokeCallbackSafely(action: () -> Unit) {
        try {
            action()
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.AppStateCallbackFail, ex)
        }
    }

    override fun addListener(listener: ProcessStateListener) {
        when (listener) {
            is SessionOrchestrator -> sessionOrchestrator = listener
            else -> listeners.addIfAbsent(listener)
        }
    }

    override fun getAppState(): ProcessState = state
}
