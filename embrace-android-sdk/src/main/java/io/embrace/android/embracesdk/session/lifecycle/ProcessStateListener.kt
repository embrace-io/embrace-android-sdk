package io.embrace.android.embracesdk.session.lifecycle

/**
 * Listener implemented by observers of the [ProcessStateService].
 */
internal interface ProcessStateListener {

    /**
     * Triggered when the app enters the background.
     */
    fun onBackground(timestamp: Long) {}

    /**
     * Triggered when the application is resumed.
     *
     * @param coldStart   whether this is a cold start
     * @param startupTime the timestamp at which the application started
     */
    fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {}
}
