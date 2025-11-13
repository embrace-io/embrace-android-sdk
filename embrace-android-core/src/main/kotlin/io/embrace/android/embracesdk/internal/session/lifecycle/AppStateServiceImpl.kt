package io.embrace.android.embracesdk.internal.session.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service tracking the app's current process state (foreground or background) as reported
 * by ProcessLifecycleOwner.
 */
internal class AppStateServiceImpl(
    private val clock: Clock,
    private val logger: EmbLogger,
    private val lifecycleOwner: LifecycleOwner,
) : AppStateService, LifecycleEventObserver {

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
    override var isInBackground: Boolean = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        private set

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
    override fun onForeground() {
        isInBackground = false
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
    override fun onBackground() {
        isInBackground = true
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

    override fun close() {
        runCatching {
            listeners.clear()
            sessionOrchestrator = null
        }
    }

    override fun getAppState(): AppState = when {
        isInBackground -> AppState.BACKGROUND
        else -> AppState.FOREGROUND
    }

    override fun isInitialized(): Boolean {
        return sessionOrchestrator != null
    }

    override fun sessionUpdated() {
        sessionOrchestrator?.onSessionDataUpdate()
    }

    companion object {
        const val FOREGROUND_STATE: String = "foreground"
        const val BACKGROUND_STATE: String = "background"
    }
}
