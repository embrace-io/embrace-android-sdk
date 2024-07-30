package io.embrace.android.embracesdk.internal.anr

/**
 * Listens to when a thread is blocked and provides actions.
 */
public interface BlockedThreadListener {

    /**
     * Called when a thread becomes blocked for at least the configured
     * interval in [Config.AnrConfig.getIntervalMs]
     */
    public fun onThreadBlocked(thread: Thread, timestamp: Long)

    /**
     * Called when a thread is already blocked and hits another interval as configured
     * in [Config.AnrConfig.getIntervalMs]
     */
    public fun onThreadBlockedInterval(thread: Thread, timestamp: Long)

    /**
     * Called when a thread becomes unblocked, after being blocked for at least the configured
     * interval in [Config.AnrConfig.getIntervalMs]
     */
    public fun onThreadUnblocked(thread: Thread, timestamp: Long)
}
