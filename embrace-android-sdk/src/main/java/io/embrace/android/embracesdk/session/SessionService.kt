package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener

internal interface SessionService : ProcessStateListener {

    /**
     * Ends a session manually. If [clearUserInfo] is true, the user info will be cleared.
     */
    fun endSessionManually(clearUserInfo: Boolean)

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun handleCrash(crashId: String)
}
