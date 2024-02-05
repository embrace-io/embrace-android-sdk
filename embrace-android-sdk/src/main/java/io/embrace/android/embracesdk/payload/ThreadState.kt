package io.embrace.android.embracesdk.payload

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

internal enum class ThreadState(internal val code: Int) {
    NEW(0),
    RUNNABLE(1),
    BLOCKED(2),
    WAITING(3),
    TIMED_WAITING(4),
    TERMINATED(5)
}
