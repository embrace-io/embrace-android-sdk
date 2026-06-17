package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes

/**
 * Holds metadata about the user session. Creating new instances with updated attributes is only permitted to support specific
 * user session state mutations:
 *
 * - Update with a new [partIndex]: [withPartIndex]
 * - Update last time the user session was active: [Classified.withNewActivity]
 * - Classify whether this user session is background-only: [Unclassified.classify])
 */
sealed interface UserSessionMetadata {
    val startTimeMs: Long
    val userSessionId: String
    val userSessionNumber: Long
    val maxDurationSecs: Long
    val inactivityTimeoutSecs: Long
    val partIndex: Int
    val lastActivityMs: Long

    /**
     * Whether this user session is background-only, or null if that is not yet known because the startup has not been classified.
     */
    val isBackgroundOnly: Boolean?

    /**
     * Create a new metadata instance of this user session with the give index of the last created part
     */
    fun withPartIndex(updatedPartIndex: Int): UserSessionMetadata

    fun isOverMaxDuration(clock: Clock): Boolean =
        clock.now() - startTimeMs > maxDurationSecs * 1_000L

    fun isInactive(clock: Clock): Boolean =
        clock.now() - lastActivityMs > inactivityTimeoutSecs * 1_000L

    val attributes: Map<String, Any>
        get() = buildMap {
            put(EmbSessionAttributes.EMB_USER_SESSION_START_TS, startTimeMs)
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
            put(EmbSessionAttributes.EMB_USER_SESSION_NUMBER, userSessionNumber)
            put(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS, maxDurationSecs)
            put(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS, inactivityTimeoutSecs)
            put(SessionAttributes.SESSION_ID, userSessionId)
            if (isBackgroundOnly == true) {
                put(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, BACKGROUND_ONLY_MARKER)
            }
        }

    /**
     * A user session where whether it is background-only or not is known
     */
    class Classified(
        override val startTimeMs: Long,
        override val userSessionId: String,
        override val userSessionNumber: Long,
        override val maxDurationSecs: Long,
        override val inactivityTimeoutSecs: Long,
        override val partIndex: Int,
        override val lastActivityMs: Long,
        override val isBackgroundOnly: Boolean,
    ) : UserSessionMetadata {

        override fun withPartIndex(updatedPartIndex: Int): Classified = Classified(
            startTimeMs = startTimeMs,
            userSessionId = userSessionId,
            userSessionNumber = userSessionNumber,
            maxDurationSecs = maxDurationSecs,
            inactivityTimeoutSecs = inactivityTimeoutSecs,
            partIndex = updatedPartIndex,
            lastActivityMs = lastActivityMs,
            isBackgroundOnly = isBackgroundOnly,
        )

        /**
         * This user session with its last activity recorded at the given time.
         */
        fun withNewActivity(lastActivityTimeMs: Long): Classified = Classified(
            startTimeMs = startTimeMs,
            userSessionId = userSessionId,
            userSessionNumber = userSessionNumber,
            maxDurationSecs = maxDurationSecs,
            inactivityTimeoutSecs = inactivityTimeoutSecs,
            partIndex = partIndex,
            lastActivityMs = lastActivityTimeMs,
            isBackgroundOnly = isBackgroundOnly,
        )
    }

    /**
     * A user session created at process start whose startup classification has not resolved. Whether it's background-only or not
     * is not yet determined.
     */
    class Unclassified(
        override val startTimeMs: Long,
        override val userSessionId: String,
        override val userSessionNumber: Long,
        override val maxDurationSecs: Long,
        override val inactivityTimeoutSecs: Long,
        override val partIndex: Int,
        override val lastActivityMs: Long,
    ) : UserSessionMetadata {

        override val isBackgroundOnly: Boolean?
            get() = null

        override fun withPartIndex(updatedPartIndex: Int): Unclassified = Unclassified(
            startTimeMs = startTimeMs,
            userSessionId = userSessionId,
            userSessionNumber = userSessionNumber,
            maxDurationSecs = maxDurationSecs,
            inactivityTimeoutSecs = inactivityTimeoutSecs,
            partIndex = updatedPartIndex,
            lastActivityMs = lastActivityMs,
        )

        /**
         * Create a new [Classified] instance of this user session's metadata when whether it is background-only can be determined.
         * Optionally update the [lastActivityMs] if the classification implies there is new user activity.
         */
        fun classify(
            isBackgroundOnly: Boolean,
            updatedLastActivityMs: Long = lastActivityMs
        ): Classified = Classified(
            startTimeMs = startTimeMs,
            userSessionId = userSessionId,
            userSessionNumber = userSessionNumber,
            maxDurationSecs = maxDurationSecs,
            inactivityTimeoutSecs = inactivityTimeoutSecs,
            partIndex = partIndex,
            lastActivityMs = updatedLastActivityMs,
            isBackgroundOnly = isBackgroundOnly,
        )
    }

    companion object {
        /**
         * The value of the [EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART] attribute when
         * the user session is background-only. The attribute is omitted otherwise.
         */
        internal const val BACKGROUND_ONLY_MARKER = "1"
    }
}
