package io.embrace.android.embracesdk.internal.session

/**
 * Describes the possible states of a user session.
 */
enum class UserSessionState {

    /**
     * No user session is active and none has been created previously.
     */
    NO_ACTIVE_USER_SESSION,

    /**
     * A user session is active.
     */
    USER_SESSION_ACTIVE,

    /**
     * A user session was previously active but has now terminated.
     */
    USER_SESSION_TERMINATED,
}
