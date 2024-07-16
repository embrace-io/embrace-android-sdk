package io.embrace.android.embracesdk.internal.anr

/**
 * Listens to when a thread is blocked and provides actions.
 */
internal interface BlockedThreadListener {

    /**
     * Called when a thread becomes blocked for at least the configured
     * interval in [Config.AnrConfig.getIntervalMs]
     */
    fun onThreadBlocked(thread: Thread, timestamp: Long)

    /**
     * Called when a thread is already blocked and hits another interval as configured
     * in [Config.AnrConfig.getIntervalMs]
     */
    fun onThreadBlockedInterval(thread: Thread, timestamp: Long)

    /**
     * Called when a thread becomes unblocked, after being blocked for at least the configured
     * interval in [Config.AnrConfig.getIntervalMs]
     */
    fun onThreadUnblocked(thread: Thread, timestamp: Long)
}
