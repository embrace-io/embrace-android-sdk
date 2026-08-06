package io.embrace.android.embracesdk.internal.session.lifecycle

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener

/**
 * Detects when the app process moves between the foreground and the background by observing and reporting transitions.
 */
interface LifecycleTracker {

    /**
     * Returns the process state as currently reported by the platform.
     */
    fun getProcessState(): ProcessState

    /**
     * Starts observing the platform, reporting every subsequent transition to [listener].
     */
    fun register(listener: ProcessStateListener)
}
