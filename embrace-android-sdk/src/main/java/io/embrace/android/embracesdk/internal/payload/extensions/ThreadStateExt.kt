package io.embrace.android.embracesdk.internal.payload.extensions

import io.embrace.android.embracesdk.internal.payload.ThreadState
import java.lang.Thread.State

internal fun mapThreadState(state: State) =
    when (state) {
        State.NEW -> ThreadState.NEW
        State.RUNNABLE -> ThreadState.RUNNABLE
        State.BLOCKED -> ThreadState.BLOCKED
        State.WAITING -> ThreadState.WAITING
        State.TIMED_WAITING -> ThreadState.TIMED_WAITING
        State.TERMINATED -> ThreadState.TERMINATED
    }
