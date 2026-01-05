package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * Listener for when a thread is blocked for at least a configured interval
 */
fun interface ThreadBlockageListener {

    /**
     * Called when a thread becomes blocked
     */
    fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    )
}
