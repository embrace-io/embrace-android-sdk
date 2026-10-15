package io.embrace.android.embracesdk.internal.session.lifecycle

import io.embrace.android.embracesdk.internal.arch.lifecycle.ProcessChannels
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.arch.state.addProcessStateListener
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Notifies the rest of the SDK whenever the app's process state changes, emitting each state on the [eventBus] before the
 * session orchestrator reacts to it.
 */
internal class ProcessStateTrackerImpl(
    private val logger: InternalLogger,
    private val lifecycleTracker: LifecycleTracker,
    private val eventBus: EventBus,
) : ProcessStateTracker, ProcessStateListener {

    private var sessionOrchestrator: SessionOrchestrator? = null

    /**
     * Starts tracking process state. Must be called exactly once, immediately after construction -
     * transitions that happen before this are not observed.
     */
    fun register() {
        emitIfChanged(lifecycleTracker.getProcessState())
        lifecycleTracker.register(this)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the foreground.
     */
    override fun onForeground() {
        emitIfChanged(ProcessState.FOREGROUND)
        invokeCallbackSafely { sessionOrchestrator?.onForeground() }
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the background.
     */
    override fun onBackground() {
        emitIfChanged(ProcessState.BACKGROUND)
        invokeCallbackSafely { sessionOrchestrator?.onBackground() }
    }

    private fun emitIfChanged(state: ProcessState) {
        if (eventBus.currentState(ProcessChannels.ProcessState) != state) {
            eventBus.emitState(ProcessChannels.ProcessState, state)
        }
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
            else -> eventBus.addProcessStateListener(listener)
        }
    }

    override fun getAppState(): ProcessState = lifecycleTracker.getProcessState()

    override fun close() {
        lifecycleTracker.close()
        sessionOrchestrator = null
    }
}
