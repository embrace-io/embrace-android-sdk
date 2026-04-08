package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
class SessionBehaviorImpl(private val remote: RemoteConfig?) : SessionBehavior {

    companion object {
        const val SESSION_PROPERTY_LIMIT: Int = 100
        const val SESSION_PROPERTY_MAX_LIMIT: Int = 200
        private const val DEFAULT_MAX_SESSION_DURATION_MINUTES: Int = 24 * 60
        private const val DEFAULT_INACTIVITY_TIMEOUT_MINUTES: Int = 30
        private const val MINUTES_TO_MS: Long = 60_000L
        private const val MIN_SESSION_MS: Long = 5_000L
    }

    private val maxSessionDurationMins by lazy {
        val override = remote?.userSession?.maxDurationMinutes
        when {
            override != null && override > 0 -> override
            else -> DEFAULT_MAX_SESSION_DURATION_MINUTES
        }
    }

    private val maxInactivityTimeoutMins by lazy {
        val override = remote?.userSession?.inactivityTimeoutMinutes
        when {
            override != null && override > 0 -> override
            else -> DEFAULT_INACTIVITY_TIMEOUT_MINUTES
        }
    }

    override fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    override fun getMaxUserSessionProperties(): Int = min(
        remote?.maxUserSessionProperties ?: SESSION_PROPERTY_LIMIT,
        SESSION_PROPERTY_MAX_LIMIT
    )

    override fun getMaxSessionDurationMs(): Long {
        return maxSessionDurationMins * MINUTES_TO_MS
    }

    override fun getSessionInactivityTimeoutMs(): Long {
        val timeoutMs = when {
            maxInactivityTimeoutMins <= maxSessionDurationMins -> maxInactivityTimeoutMins
            else -> DEFAULT_INACTIVITY_TIMEOUT_MINUTES
        }
        return timeoutMs * MINUTES_TO_MS
    }

    override fun getMinSessionDurationMs(): Long = MIN_SESSION_MS
}
