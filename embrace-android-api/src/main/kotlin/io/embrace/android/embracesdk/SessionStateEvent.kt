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
     *
     * It's important to note that the same userSessionId value may be present in multiple, distinct events of
     * [io.embrace.android.embracesdk.SessionStateEvent.UserSessionActive]. This API is primarily intended so that
     * library consumers can perform operations when a user session is brought into memory, such as setting
     * a user ID or user session properties.
     */
    public class UserSessionActive(userSessionId: String) : SessionStateEvent(userSessionId)

    /**
     * The current user session has ended.
     */
    public class UserSessionEnded(userSessionId: String) : SessionStateEvent(userSessionId)
}
