package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * Factory that creates session + background activity payloads.
 */
internal interface PayloadFactory {

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
     * Provides a snapshot of the active background activity
     */
    fun snapshotBackgroundActivity(initial: Session, timestamp: Long): SessionMessage

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
