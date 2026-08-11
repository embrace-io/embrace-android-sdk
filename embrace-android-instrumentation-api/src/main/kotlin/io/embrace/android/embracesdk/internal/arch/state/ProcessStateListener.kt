package io.embrace.android.embracesdk.internal.arch.state

/**
 * Listener implemented by observers of the [ProcessStateTracker].
 */
interface ProcessStateListener {

    /**
     * Triggered when the app enters the background.
     */
    fun onBackground()

    /**
     * Triggered when the application is resumed.
     */
    fun onForeground()
}
