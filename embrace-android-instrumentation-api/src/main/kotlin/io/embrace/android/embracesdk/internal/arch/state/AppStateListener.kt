package io.embrace.android.embracesdk.internal.arch.state

/**
 * Listener implemented by observers of the [AppStateTracker].
 */
interface AppStateListener {

    /**
     * Triggered when the app enters the background.
     */
    fun onBackground(timestamp: Long) {}

    /**
     * Triggered when the application is resumed.
     *
     * @param coldStart   whether this is a cold start
     * @param timestamp the timestamp at which the application entered the foreground
     */
    fun onForeground(coldStart: Boolean, timestamp: Long) {}
}
