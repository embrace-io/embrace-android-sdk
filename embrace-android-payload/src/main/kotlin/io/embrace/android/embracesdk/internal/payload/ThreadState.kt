package io.embrace.android.embracesdk.internal.payload

public enum class ThreadState(public val code: Int) {
    NEW(0),
    RUNNABLE(1),
    BLOCKED(2),
    WAITING(3),
    TIMED_WAITING(4),
    TERMINATED(5)
}
