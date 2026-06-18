package io.embrace.android.embracesdk.internal.session

/**
 * The decision this SDK instance made about the persisted user session when it starts up, i.e. whether to continue or terminate it
 */
sealed interface UserSessionRestoreDecision {
    val userSessionId: String

    /**
     * Whether the persisted user session was background-only
     */
    val backgroundOnly: Boolean

    /**
     * The persisted session was continued.
     */
    data class Restored(
        override val userSessionId: String,
        override val backgroundOnly: Boolean,
    ) : UserSessionRestoreDecision

    /**
     * The persisted session was not continued and implicitly terminated.
     */
    data class Terminated(
        override val userSessionId: String,
        override val backgroundOnly: Boolean,
        val reason: String,
    ) : UserSessionRestoreDecision
}
