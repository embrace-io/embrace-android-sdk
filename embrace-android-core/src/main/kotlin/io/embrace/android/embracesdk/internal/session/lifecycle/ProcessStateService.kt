package io.embrace.android.embracesdk.internal.session.lifecycle

import androidx.lifecycle.LifecycleEventObserver
import java.io.Closeable

/**
 * Service which handles Android process lifecycle callbacks.
 */
interface ProcessStateService : LifecycleEventObserver, Closeable {

    /**
     * Whether the application is in the background.
     *
     * @return true if the application is in the background, false otherwise
     */
    val isInBackground: Boolean

    /**
     * Adds an observer of the application's process lifecycle events.
     *
     * @param listener the observer to register
     */
    fun addListener(listener: ProcessStateListener)

    /**
     * This function should be automatically invoked when the process lifecycle
     * enters the foreground. You should not call this directly.
     */
    fun onForeground()

    /**
     * This function should be automatically invoked when the process lifecycle
     * enters the background. You should not call this directly.
     */
    fun onBackground()

    /**
     * Returns 'foreground' if the application is in the foreground, or 'background' if the app is in
     * the background.
     *
     * @return the current state of the app
     */
    fun getAppState(): String

    /**
     * Returns true if this is fully initialized
     */
    fun isInitialized(): Boolean
}
