package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * The state of the thread.
 */
internal enum class ThreadBlockageEvent {

    /**
     * When the thread becomes blocked (i.e. not responsive for X milliseconds)
     */
    BLOCKED,

    /**
     * Invoked when the thread first becomes blocked and at regular intervals until it becomes unblocked.
     */
    BLOCKED_INTERVAL,

    /**
     * When the thread becomes unblocked (i.e. responds to messages after being in the BLOCKED state)
     */
    UNBLOCKED,
}
