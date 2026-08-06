package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker

class FakeProcessStateTracker(
    var state: ProcessState = ProcessState.FOREGROUND,
) : ProcessStateTracker {

    val listeners: MutableList<ProcessStateListener> = mutableListOf()

    override fun addListener(listener: ProcessStateListener) {
        listeners.add(listener)
    }

    override fun getAppState(): ProcessState = state
}
