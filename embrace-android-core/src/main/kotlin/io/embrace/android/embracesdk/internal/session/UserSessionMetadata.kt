package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes

/**
 * Holds metadata about the user session.
 */
data class UserSessionMetadata(
    val startTimeMs: Long,
    val userSessionId: String,
    val userSessionNumber: Long,
    val maxDurationSecs: Long,
    val inactivityTimeoutSecs: Long,
    val partNumber: Int,
    val lastActivityMs: Long,
) {
    val attributes: Map<String, Any> = mapOf(
        EmbSessionAttributes.EMB_USER_SESSION_START_TS to startTimeMs,
        EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId,
        EmbSessionAttributes.EMB_USER_SESSION_NUMBER to userSessionNumber,
        EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS to maxDurationSecs,
        EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS to inactivityTimeoutSecs,
        EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER to partNumber,
        SessionAttributes.SESSION_ID to userSessionId,
    )
}
