package io.embrace.android.embracesdk.internal.arch.state

/**
 * Service which handles Android process lifecycle callbacks.
 */
interface ProcessStateTracker : AutoCloseable {

    /**
     * Starts observing the platform for process lifecycle events. Must be called exactly once - transitions that
     * happen before this are not observed.
     */
    fun register()

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
