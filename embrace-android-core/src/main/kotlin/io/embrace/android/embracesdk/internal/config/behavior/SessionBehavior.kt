package io.embrace.android.embracesdk.internal.config.behavior

interface SessionBehavior {

    /**
     * The whitelist of events (crashes, errors) that should send a full session payload even
     * if the gating feature is enabled.
     *
     * @return a whitelist of events allowed to send full session payloads
     */
    fun getFullSessionEvents(): Set<String>

    /**
     * Returns the session components that should be recorded (e.g. breadcrumbs).
     */
    fun getSessionComponents(): Set<String>?

    /**
     * Determines if the gating feature is enabled based on the presence of the session
     * components list property.
     *
     * @return true if the gating feature is enabled
     */
    fun isGatingFeatureEnabled(): Boolean

    /**
     * Whether session control is disabled, meaning that can't end sessions manually.
     */
    fun isSessionControlDisabled(): Boolean

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    fun getMaxSessionProperties(): Int

    /**
     * Check if should gate Moment based on gating config.
     */
    fun shouldGateMoment(): Boolean

    /**
     * Check if should gate Info Logs based on gating config.
     */
    fun shouldGateInfoLog(): Boolean

    /**
     * Check if should gate Warning Logs based on gating config.
     */
    fun shouldGateWarnLog(): Boolean

    /**
     * Check if should gate Startup moment based on gating config.
     */
    fun shouldGateStartupMoment(): Boolean

    /**
     * Checks if a full payload should be sent for a session with an associated crash
     */
    fun shouldSendFullForCrash(): Boolean

    /**
     * Checks if a full payload should be sent for a session with an associated error log
     */
    fun shouldSendFullForErrorLog(): Boolean

    /**
     * Check if should gate Session Properties based on gating config.
     */
    fun shouldGateSessionProperties(): Boolean

    /**
     * Check if should gate Log Properties based on gating config.
     */
    fun shouldGateLogProperties(): Boolean
}
