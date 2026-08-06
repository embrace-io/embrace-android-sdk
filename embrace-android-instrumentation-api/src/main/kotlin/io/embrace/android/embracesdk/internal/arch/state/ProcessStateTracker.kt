package io.embrace.android.embracesdk.internal.arch.state

/**
 * Service which handles Android process lifecycle callbacks.
 */
interface ProcessStateTracker {

    /**
     * Adds an observer of the application's process lifecycle events.
     *
     * @param listener the observer to register
     */
    fun addListener(listener: ProcessStateListener)

    /**
     * Returns 'foreground' if the application is in the foreground, or 'background' if the app is in
     * the background.
     *
     * @return the current state of the app
     */
    fun getAppState(): ProcessState
}
