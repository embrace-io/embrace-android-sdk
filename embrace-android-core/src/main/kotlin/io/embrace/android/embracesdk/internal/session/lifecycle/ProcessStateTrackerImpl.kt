package io.embrace.android.embracesdk.internal.session.lifecycle

import io.embrace.android.embracesdk.internal.arch.lifecycle.ProcessChannels
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Notifies the rest of the SDK whenever the app's process state changes, emitting each change on the [eventBus] first.
 */
internal class ProcessStateTrackerImpl(
    private val errorHandler: ErrorHandler,
    private val lifecycleTracker: LifecycleTracker,
    private val eventBus: EventBus,
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
        emitIfChanged(lifecycleTracker.getProcessState())
        lifecycleTracker.register(this)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the foreground.
     */
    override fun onForeground() {
        emitIfChanged(ProcessState.FOREGROUND)
        errorHandler.guardSdkCode(SdkErrorType.AppStateCallbackFail) { sessionOrchestrator?.onForeground() }
        errorHandler.guardSdkCodeEach(SdkErrorType.AppStateCallbackFail, listeners.toList(), ProcessStateListener::onForeground)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the background.
     */
    override fun onBackground() {
        emitIfChanged(ProcessState.BACKGROUND)
        errorHandler.guardSdkCodeEach(SdkErrorType.AppStateCallbackFail, listeners.toList(), ProcessStateListener::onBackground)
        errorHandler.guardSdkCode(SdkErrorType.AppStateCallbackFail) { sessionOrchestrator?.onBackground() }
    }

    private fun emitIfChanged(state: ProcessState) {
        if (eventBus.currentState(ProcessChannels.ProcessState) != state) {
            eventBus.emitState(ProcessChannels.ProcessState, state)
        }
    }

    override fun addListener(listener: ProcessStateListener) {
        when (listener) {
            is SessionOrchestrator -> sessionOrchestrator = listener
            else -> listeners.addIfAbsent(listener)
        }
    }

    override fun getAppState(): ProcessState = lifecycleTracker.getProcessState()

    override fun close() {
        errorHandler.guardSdkCode(SdkErrorType.ResourceCloseFail) { lifecycleTracker.close() }
        listeners.clear()
        sessionOrchestrator = null
    }
}
