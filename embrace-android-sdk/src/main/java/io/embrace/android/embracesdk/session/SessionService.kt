package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import java.io.Closeable

internal interface SessionService : ProcessStateListener, Closeable {

    /**
     * Starts a new session.
     *
     * @param coldStart whether this is a cold start of the application
     * @param startType the origin of the event that starts the session.
     */
    fun startSession(coldStart: Boolean, startType: LifeEventType, startTime: Long)

    fun endSessionManually(clearUserInfo: Boolean)

    /**
     * This is responsible for the current session to be cached, ended and sent to the server and
     * immediately starting a new session after that.
     *
     * @param endType the origin of the event that ends the session.
     */
    fun triggerStatelessSessionEnd(endType: LifeEventType, clearUserInfo: Boolean = false)

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun handleCrash(crashId: String)
}
