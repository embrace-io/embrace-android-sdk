package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

/**
 * Listener for when a thread is blocked for at least a configured interval
 */
interface BlockedThreadListener {

    /**
     * Called when a thread becomes blocked
     */
    fun onThreadBlocked(thread: Thread, timestamp: Long)

    /**
     * Periodically called at regular intervals when a thread is blocked.
     */
    fun onThreadBlockedInterval(thread: Thread, timestamp: Long)

    /**
     * Called when a thread becomes unblocked
     */
    fun onThreadUnblocked(thread: Thread, timestamp: Long)
}
