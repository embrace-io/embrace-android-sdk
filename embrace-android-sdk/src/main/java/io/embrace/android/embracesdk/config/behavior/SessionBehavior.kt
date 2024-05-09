package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.EventMessage
import java.util.Locale

/**
 * Provides the behavior that functionality relating to sessions should follow.
 */
internal class SessionBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SessionLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<SessionLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        const val SESSION_PROPERTY_LIMIT = 10
    }

    /**
     * The whitelist of events (crashes, errors) that should send a full session payload even
     * if the gating feature is enabled.
     *
     * @return a whitelist of events allowed to send full session payloads
     */
    fun getFullSessionEvents(): Set<String> {
        val strings = remote?.sessionConfig?.fullSessionEvents ?: local?.fullSessionEvents ?: emptySet()
        return strings.map { it.toLowerCase(Locale.US) }.toSet()
    }

    /**
     * Returns the session components that should be recorded (e.g. breadcrumbs).
     */
    fun getSessionComponents(): Set<String>? =
        remote?.sessionConfig?.sessionComponents ?: local?.sessionComponents

    /**
     * Determines if the gating feature is enabled based on the presence of the session
     * components list property.
     *
     * @return true if the gating feature is enabled
     */
    fun isGatingFeatureEnabled(): Boolean = getSessionComponents() != null

    /**
     * Whether session control is enabled, meaning that features should be gated.
     */
    fun isSessionControlEnabled(): Boolean = remote?.sessionConfig?.isEnabled ?: false

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    fun getMaxSessionProperties(): Int = remote?.maxSessionProperties ?: SESSION_PROPERTY_LIMIT

    /**
     * Check if should gate Moment based on gating config.
     */
    fun shouldGateMoment() = shouldGateFeature(SessionGatingKeys.SESSION_MOMENTS)

    /**
     * Check if should gate Info Logs based on gating config.
     */
    fun shouldGateInfoLog() = shouldGateFeature(SessionGatingKeys.LOGS_INFO)

    /**
     * Check if should gate Warning Logs based on gating config.
     */
    fun shouldGateWarnLog() = shouldGateFeature(SessionGatingKeys.LOGS_WARN)

    /**
     * Check if should gate Startup moment based on gating config.
     */
    fun shouldGateStartupMoment() = shouldGateFeature(SessionGatingKeys.STARTUP_MOMENT)

    /**
     * Whether a full payload should be sent for this [EventMessage] or whether payload fields
     * should be gated.
     */
    fun shouldSendFullMessage(eventMessage: EventMessage): Boolean {
        val type = eventMessage.event.type
        return (type == EventType.ERROR_LOG && shouldSendFullForErrorLog()) ||
            (type == EventType.CRASH && shouldSendFullForCrash())
    }

    /**
     * Checks if a full payload should be sent for a session with an associated crash
     */
    fun shouldSendFullForCrash() =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_CRASHES)

    /**
     * Checks if a full payload should be sent for a session with an associated error log
     */
    fun shouldSendFullForErrorLog() =
        getFullSessionEvents().contains(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)

    /**
     * Check if should gate Session Properties based on gating config.
     */
    fun shouldGateSessionProperties() =
        shouldGateFeature(SessionGatingKeys.SESSION_PROPERTIES)

    /**
     * Check if should gate Log Properties based on gating config.
     */
    fun shouldGateLogProperties() =
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
