package io.embrace.android.embracesdk.internal.session.lifecycle

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Notifies the rest of the SDK whenever the app's process state changes.
 */
internal class ProcessStateTrackerImpl(
    private val logger: InternalLogger,
    private val lifecycleTracker: LifecycleTracker,
) : ProcessStateTracker, ProcessStateListener {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners: CopyOnWriteArrayList<ProcessStateListener> = CopyOnWriteArrayList<ProcessStateListener>()

    private var sessionOrchestrator: SessionOrchestrator? = null

    /**
     * Starts tracking process state. Must be called exactly once, immediately after construction -
     * transitions that happen before this are not observed.
     */
    fun register() {
        lifecycleTracker.register(this)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the foreground.
     */
    override fun onForeground() {
        invokeCallbackSafely { sessionOrchestrator?.onForeground() }

        listeners.toList().forEach { listener: ProcessStateListener ->
            invokeCallbackSafely {
                listener.onForeground()
            }
        }
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the background.
     */
    override fun onBackground() {
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

    override fun getAppState(): ProcessState = lifecycleTracker.getProcessState()
}
