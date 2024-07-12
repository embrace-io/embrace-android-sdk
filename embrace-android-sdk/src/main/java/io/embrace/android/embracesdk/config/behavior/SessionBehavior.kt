package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface SessionBehavior {

    /**
     * The whitelist of events (crashes, errors) that should send a full session payload even
     * if the gating feature is enabled.
     *
     * @return a whitelist of events allowed to send full session payloads
     */
    public fun getFullSessionEvents(): Set<String>

    /**
     * Returns the session components that should be recorded (e.g. breadcrumbs).
     */
    public fun getSessionComponents(): Set<String>?

    /**
     * Determines if the gating feature is enabled based on the presence of the session
     * components list property.
     *
     * @return true if the gating feature is enabled
     */
    public fun isGatingFeatureEnabled(): Boolean

    /**
     * Whether session control is enabled, meaning that features should be gated.
     */
    public fun isSessionControlEnabled(): Boolean

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    public fun getMaxSessionProperties(): Int

    /**
     * Check if should gate Moment based on gating config.
     */
    public fun shouldGateMoment(): Boolean

    /**
     * Check if should gate Info Logs based on gating config.
     */
    public fun shouldGateInfoLog(): Boolean

    /**
     * Check if should gate Warning Logs based on gating config.
     */
    public fun shouldGateWarnLog(): Boolean

    /**
     * Check if should gate Startup moment based on gating config.
     */
    public fun shouldGateStartupMoment(): Boolean

    /**
     * Checks if a full payload should be sent for a session with an associated crash
     */
    public fun shouldSendFullForCrash(): Boolean

    /**
     * Checks if a full payload should be sent for a session with an associated error log
     */
    public fun shouldSendFullForErrorLog(): Boolean

    /**
     * Check if should gate Session Properties based on gating config.
     */
    public fun shouldGateSessionProperties(): Boolean

    /**
     * Check if should gate Log Properties based on gating config.
     */
    public fun shouldGateLogProperties(): Boolean
}
