package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.SessionConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys
import java.util.Locale
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
class SessionBehaviorImpl(
    local: InstrumentedConfig,
    override val remote: RemoteConfig?,
) : SessionBehavior {

    companion object {
        const val SESSION_PROPERTY_LIMIT: Int = 100
        const val SESSION_PROPERTY_MAX_LIMIT: Int = 200
    }

    override val local: SessionConfig = local.session

    override fun getFullSessionEvents(): Set<String> {
        val strings = remote?.sessionConfig?.fullSessionEvents ?: local.getFullSessionEvents()
        return strings.map { it.lowercase(Locale.US) }.toSet()
    }

    override fun getSessionComponents(): Set<String>? =
        (remote?.sessionConfig?.sessionComponents ?: local.getSessionComponents())?.toSet()

    override fun isGatingFeatureEnabled(): Boolean = getSessionComponents() != null

    override fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    override fun getMaxSessionProperties(): Int = min(remote?.maxSessionProperties ?: SESSION_PROPERTY_LIMIT, SESSION_PROPERTY_MAX_LIMIT)

    override fun shouldGateInfoLog(): Boolean = shouldGateFeature(SessionGatingKeys.LOGS_INFO)

    override fun shouldGateWarnLog(): Boolean = shouldGateFeature(SessionGatingKeys.LOGS_WARN)

    override fun shouldSendFullForCrash(): Boolean =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_CRASHES)

    override fun shouldSendFullForErrorLog(): Boolean =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)

    override fun shouldGateSessionProperties(): Boolean =
        shouldGateFeature(SessionGatingKeys.SESSION_PROPERTIES)

    override fun shouldGateLogProperties(): Boolean =
        shouldGateFeature(SessionGatingKeys.LOG_PROPERTIES)

    /**
     * Checks whether a feature should be gated.
     * If [getSessionComponents] is null, this will return false.
     * If [getSessionComponents] is empty, this will return true.
     * If [getSessionComponents] contains the key representing the feature, this will return false.
     * Otherwise, this will return true.
     */
    private fun shouldGateFeature(key: String) =
        getSessionComponents()?.let { !it.contains(key) } ?: false
}
