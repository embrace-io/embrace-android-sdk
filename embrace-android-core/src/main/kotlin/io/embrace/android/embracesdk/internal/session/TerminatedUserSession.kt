package io.embrace.android.embracesdk.internal.session

/**
 * A user session that the has SDK determined should not continue when a new process starts up.
 * Used during payload resurrection when dealing with the last session part of the user session terminated the
 * startup of the current process in order to mark it as that session's final part and provide a reason for the
 * termination.
 */
data class TerminatedUserSession(
    val userSessionId: String,
    val reason: String,
)
