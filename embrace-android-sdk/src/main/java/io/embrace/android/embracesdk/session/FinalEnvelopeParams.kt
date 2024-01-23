package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.payload.Session

/**
 * Holds the parameters & logic needed to create a final session object that can be
 * sent to the backend.
 */
internal sealed class FinalEnvelopeParams(
    val initial: Session,
    val endTime: Long,
    val lifeEventType: Session.LifeEventType?,
    crashId: String?,
    val isCacheAttempt: Boolean
) {

    val crashId: String? = when {
        crashId.isNullOrEmpty() -> null
        else -> crashId
    }

    /**
     * Initial parameters required to create a background activity object.
     */
    internal class BackgroundActivityParams(
        initial: Session,
        endTime: Long,
        endType: Session.LifeEventType?,
        crashId: String?,
        isCacheAttempt: Boolean

        /**
         * This is an attempt at periodic caching, not a true end message.
         */
    ) : FinalEnvelopeParams(
        initial,
        endTime,
        endType,
        crashId,
        isCacheAttempt
    )

    /**
     * Initial parameters required to create a session object.
     */
    internal class SessionParams(
        initial: Session,
        endTime: Long,
        endType: Session.LifeEventType?,
        crashId: String?,
        isCacheAttempt: Boolean
    ) : FinalEnvelopeParams(
        initial,
        endTime,
        endType,
        crashId,
        isCacheAttempt
    )
}
