package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener

/**
 * Service that captures and sends information when the app is in background
 */
internal interface BackgroundActivityService : ProcessStateListener {

    /**
     * Stops the current background activity session and sends the session message to the backend
     */
    fun sendBackgroundActivity()

    /**
     * Handles an uncaught exception, ending the session and saving the activity to disk.
     */
    fun handleCrash(crashId: String)

    /**
     * Save the current background activity to disk
     */
    fun save()
}
