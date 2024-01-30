package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage

internal interface SessionService {

    /**
     * Starts a session in response to a state event.
     */
    fun startSessionWithState(timestamp: Long, coldStart: Boolean): Session

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(timestamp: Long): Session

    /**
     * Ends a session in response to a state event.
     */
    fun endSessionWithState(initial: Session, timestamp: Long)

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual(initial: Session, timestamp: Long)

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endSessionWithCrash(initial: Session, timestamp: Long, crashId: String)

    /**
     * Provides a snapshot of the active session
     */
    fun snapshotSession(initial: Session, timestamp: Long): SessionMessage?
}
