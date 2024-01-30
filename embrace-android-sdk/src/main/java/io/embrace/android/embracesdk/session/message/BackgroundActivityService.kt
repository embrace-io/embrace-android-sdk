package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session

/**
 * Service that captures and sends information when the app is in background
 */
internal interface BackgroundActivityService {

    /**
     * Ends a background activity in response to a state event.
     */
    fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): Session

    /**
     * Handles an uncaught exception, ending the session and saving the activity to disk.
     */
    fun endBackgroundActivityWithCrash(initial: Session, timestamp: Long, crashId: String)

    /**
     * Starts a background activity in response to a state event.
     */
    fun endBackgroundActivityWithState(initial: Session, timestamp: Long)

    /**
     * Save the current background activity to disk
     */
    fun saveBackgroundActivitySnapshot(initial: Session,)
}
