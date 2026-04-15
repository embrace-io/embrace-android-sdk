package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
class UserSessionBehaviorImpl(private val remote: RemoteConfig?) : UserSessionBehavior {

    companion object {
        const val SESSION_PROPERTY_LIMIT: Int = 100
        const val SESSION_PROPERTY_MAX_LIMIT: Int = 200
        private const val MAX_DURATION_SECONDS_DEFAULT: Int = 12 * 60 * 60
        private const val MAX_DURATION_SECONDS_MIN: Int = 1 * 60 * 60
        private const val MAX_DURATION_SECONDS_MAX: Int = 24 * 60 * 60
        private const val INACTIVITY_TIMEOUT_SECONDS_DEFAULT: Int = 30 * 60
        private const val INACTIVITY_TIMEOUT_SECONDS_MAX: Int = 24 * 60 * 60
        private const val INACTIVITY_TIMEOUT_SECONDS_MIN: Int = 30
        private const val MIN_SESSION_MS: Long = 5_000L
    }

    private val maxSessionDurationSeconds by lazy {
        val override = remote?.userSession?.maxDurationSeconds
        when {
            override != null && override >= MAX_DURATION_SECONDS_MIN && override <= MAX_DURATION_SECONDS_MAX -> override
            else -> MAX_DURATION_SECONDS_DEFAULT
        }
    }

    private val maxInactivityTimeoutSeconds by lazy {
        val override = remote?.userSession?.inactivityTimeoutSeconds
        when {
            override != null && override >= INACTIVITY_TIMEOUT_SECONDS_MIN && override <= INACTIVITY_TIMEOUT_SECONDS_MAX -> override
            else -> INACTIVITY_TIMEOUT_SECONDS_DEFAULT
        }
    }

    override fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    override fun getMaxUserSessionProperties(): Int = min(
        remote?.maxUserSessionProperties ?: SESSION_PROPERTY_LIMIT,
        SESSION_PROPERTY_MAX_LIMIT
    )

    override fun getMaxSessionDurationMs(): Long = maxSessionDurationSeconds * 1000L

    override fun getSessionInactivityTimeoutMs(): Long {
        val timeoutSecs = when {
            maxInactivityTimeoutSeconds <= maxSessionDurationSeconds -> maxInactivityTimeoutSeconds
            else -> INACTIVITY_TIMEOUT_SECONDS_DEFAULT
        }
        return timeoutSecs * 1000L
    }

    override fun getMinSessionDurationMs(): Long = MIN_SESSION_MS
}
