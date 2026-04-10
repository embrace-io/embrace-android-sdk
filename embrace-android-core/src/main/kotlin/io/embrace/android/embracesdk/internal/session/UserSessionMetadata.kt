package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

/**
 * Holds metadata about the user session.
 */
internal class UserSessionMetadata(
    val startTimeMs: Long,
    val userSessionId: String,
    val userSessionNumber: Long,
    val maxDurationMins: Long,
    val inactivityTimeoutMins: Long,
) {
    val attributes: Map<String, Any> = mapOf(
        EmbSessionAttributes.EMB_USER_SESSION_START_TS to startTimeMs,
        EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId,
        EmbSessionAttributes.EMB_USER_SESSION_NUMBER to userSessionNumber,
        EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_MINUTES to maxDurationMins,
        EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_MINUTES to inactivityTimeoutMins,
    )
}
