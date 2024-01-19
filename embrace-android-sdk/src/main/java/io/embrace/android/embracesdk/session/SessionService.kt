package io.embrace.android.embracesdk.session

internal interface SessionService {

    /**
     * Starts a session in response to a state event.
     */
    fun startSessionWithState(coldStart: Boolean, timestamp: Long)

    /**
     * Ends a session in response to a state event.
     */
    fun endSessionWithState(timestamp: Long)

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endSessionWithCrash(crashId: String)

    /**
     * Ends a session manually. If [clearUserInfo] is true, the user info will be cleared.
     */
    fun endSessionWithManual(clearUserInfo: Boolean)

    fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long)
}
