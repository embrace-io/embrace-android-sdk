package io.embrace.android.embracesdk.internal.session.lifecycle

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Notifies the rest of the SDK whenever the app's process state changes.
 */
internal class ProcessStateTrackerImpl(
    private val errorHandler: ErrorHandler,
    private val lifecycleTracker: LifecycleTracker,
) : ProcessStateTracker, ProcessStateListener {

    /**
     * List of listeners that subscribe to process lifecycle events.
     */
    val listeners: CopyOnWriteArrayList<ProcessStateListener> = CopyOnWriteArrayList<ProcessStateListener>()

    private var sessionOrchestrator: SessionOrchestrator? = null

    override fun register() {
        lifecycleTracker.register(this)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the foreground.
     */
    override fun onForeground() {
        errorHandler.guardSdkCode(SdkErrorType.AppStateCallbackFail) { sessionOrchestrator?.onForeground() }
        errorHandler.guardSdkCodeEach(SdkErrorType.AppStateCallbackFail, listeners.toList(), ProcessStateListener::onForeground)
    }

    /**
     * Called by [lifecycleTracker] when the app process has entered the background.
     */
    override fun onBackground() {
        errorHandler.guardSdkCodeEach(SdkErrorType.AppStateCallbackFail, listeners.toList(), ProcessStateListener::onBackground)
        errorHandler.guardSdkCode(SdkErrorType.AppStateCallbackFail) { sessionOrchestrator?.onBackground() }
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
