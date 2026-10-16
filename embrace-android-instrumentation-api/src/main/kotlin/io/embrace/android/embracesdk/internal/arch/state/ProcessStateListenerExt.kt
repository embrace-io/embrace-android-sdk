package io.embrace.android.embracesdk.internal.arch.state

import io.embrace.android.embracesdk.internal.arch.lifecycle.ProcessChannels
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Notifies [listener] of each process state change emitted after it is registered.
 */
fun EventBus.addProcessStateListener(listener: ProcessStateListener) {
    addStateHandler(ProcessChannels.ProcessState, replay = false) { state ->
        when (state) {
            ProcessState.FOREGROUND -> listener.onForeground()
            ProcessState.BACKGROUND -> listener.onBackground()
        }
    }
}
