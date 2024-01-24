package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.payload.Session

internal interface SessionService {

    val activeSession: Session?

    /**
     * Starts a session in response to a state event.
     */
    fun startSessionWithState(coldStart: Boolean, timestamp: Long): String

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(): String

    /**
     * Ends a session in response to a state event.
     */
    fun endSessionWithState(timestamp: Long)

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual()

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endSessionWithCrash(crashId: String)
}
