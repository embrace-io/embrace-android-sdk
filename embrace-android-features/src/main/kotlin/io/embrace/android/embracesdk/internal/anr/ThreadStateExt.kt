package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.payload.ThreadState
import java.lang.Thread.State

public fun mapThreadState(state: State): ThreadState =
    when (state) {
        State.NEW -> ThreadState.NEW
        State.RUNNABLE -> ThreadState.RUNNABLE
        State.BLOCKED -> ThreadState.BLOCKED
        State.WAITING -> ThreadState.WAITING
        State.TIMED_WAITING -> ThreadState.TIMED_WAITING
        State.TERMINATED -> ThreadState.TERMINATED
    }
