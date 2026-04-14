package io.embrace.android.embracesdk

/**
 * Represents an event in the user session lifecycle.
 */
public sealed class SessionStateEvent(

    /**
     * ID representing the user session.
     */
    public val userSessionId: String
) {

    /**
     * A user session is active. This event is fired in the following conditions:
     *
     * 1. When a new user session starts
     * 2. When an existing user session is restored in a new process
     * 3. When a listener is registered, if a user session is already active
     */
    public class UserSessionActive(userSessionId: String) : SessionStateEvent(userSessionId)

    /**
     * The current user session has ended.
     */
    public class UserSessionEnded(userSessionId: String) : SessionStateEvent(userSessionId)
}
