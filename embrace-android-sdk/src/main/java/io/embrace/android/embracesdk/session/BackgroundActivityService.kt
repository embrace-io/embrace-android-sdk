package io.embrace.android.embracesdk.session

/**
 * Service that captures and sends information when the app is in background
 */
internal interface BackgroundActivityService {

    /**
     * Ends a background activity in response to a state event.
     */
    fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): String

    /**
     * Handles an uncaught exception, ending the session and saving the activity to disk.
     */
    fun endBackgroundActivityWithCrash(timestamp: Long, crashId: String)

    /**
     * Starts a background activity in response to a state event.
     */
    fun endBackgroundActivityWithState(timestamp: Long)

    /**
     * Save the current background activity to disk
     */
    fun save()
}
