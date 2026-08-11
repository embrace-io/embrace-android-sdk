package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker

internal class FakeLifecycleTracker(
    var state: ProcessState = ProcessState.BACKGROUND,
) : LifecycleTracker {

    var listener: ProcessStateListener? = null

    override fun getProcessState(): ProcessState = state

    override fun register(listener: ProcessStateListener) {
        this.listener = listener
    }
}
