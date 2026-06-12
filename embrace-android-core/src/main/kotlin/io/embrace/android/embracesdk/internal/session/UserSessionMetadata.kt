package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.clock.Clock
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
    val partIndex: Int,
    val lastActivityMs: Long,
    val isBackgroundOnly: Boolean = false,
) {
    fun isOverMaxDuration(clock: Clock): Boolean =
        clock.now() - startTimeMs > maxDurationSecs * 1_000L

    fun isInactive(clock: Clock): Boolean =
        clock.now() - lastActivityMs > inactivityTimeoutSecs * 1_000L

    val attributes: Map<String, Any> = buildMap {
        put(EmbSessionAttributes.EMB_USER_SESSION_START_TS, startTimeMs)
        put(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
        put(EmbSessionAttributes.EMB_USER_SESSION_NUMBER, userSessionNumber)
        put(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS, maxDurationSecs)
        put(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS, inactivityTimeoutSecs)
        put(SessionAttributes.SESSION_ID, userSessionId)
        if (isBackgroundOnly) {
            put(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, BACKGROUND_ONLY_MARKER)
        }
    }

    companion object {
        /**
         * The value of the [EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART] attribute when
         * the user session is background-only. The attribute is omitted otherwise.
         */
        internal const val BACKGROUND_ONLY_MARKER = "1"
    }
}
