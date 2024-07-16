package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.EventMessage
import java.util.Locale

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
internal class SessionBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SessionLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : SessionBehavior, MergedConfigBehavior<SessionLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        const val SESSION_PROPERTY_LIMIT = 10
    }

    override fun getFullSessionEvents(): Set<String> {
        val strings = remote?.sessionConfig?.fullSessionEvents ?: local?.fullSessionEvents ?: emptySet()
        return strings.map { it.toLowerCase(Locale.US) }.toSet()
    }

    override fun getSessionComponents(): Set<String>? =
        remote?.sessionConfig?.sessionComponents ?: local?.sessionComponents

    override fun isGatingFeatureEnabled(): Boolean = getSessionComponents() != null

    override fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    override fun getMaxSessionProperties(): Int = remote?.maxSessionProperties ?: SESSION_PROPERTY_LIMIT

    override fun shouldGateMoment() = shouldGateFeature(SessionGatingKeys.SESSION_MOMENTS)

    override fun shouldGateInfoLog() = shouldGateFeature(SessionGatingKeys.LOGS_INFO)

    override fun shouldGateWarnLog() = shouldGateFeature(SessionGatingKeys.LOGS_WARN)

    override fun shouldGateStartupMoment() = shouldGateFeature(SessionGatingKeys.STARTUP_MOMENT)

    fun shouldSendFullMessage(eventMessage: EventMessage): Boolean {
        val type = eventMessage.event.type
        return (type == EventType.ERROR_LOG && shouldSendFullForErrorLog()) ||
            (type == EventType.CRASH && shouldSendFullForCrash())
    }

    override fun shouldSendFullForCrash() =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_CRASHES)

    override fun shouldSendFullForErrorLog() =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)

    override fun shouldGateSessionProperties() =
        shouldGateFeature(SessionGatingKeys.SESSION_PROPERTIES)

    override fun shouldGateLogProperties() =
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
