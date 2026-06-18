package io.embrace.android.embracesdk.internal.session

/**
 * Describes the possible states of a user session.
 */
internal sealed interface UserSessionState {

    /**
     * The orchestrator is loading initial state from the persistent store.
     */
    object Initializing : UserSessionState

    /**
     * No user session is active and none has been created previously.
     */
    object NoActiveSession : UserSessionState

    /**
     * A user session is active and can host session parts. Its metadata is [UserSessionMetadata.Unclassified] until the startup
     * classification resolves and [UserSessionMetadata.Classified] after.
     */
    data class Active(val metadata: UserSessionMetadata) : UserSessionState

    /**
     * A user session was previously active but has now terminated.
     */
    object Terminated : UserSessionState
}
