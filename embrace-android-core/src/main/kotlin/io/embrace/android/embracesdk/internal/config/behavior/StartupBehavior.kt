package io.embrace.android.embracesdk.internal.config.behavior

interface StartupBehavior {

    /**
     * Controls whether the startup moment is automatically ended.
     */
    fun isAutomaticEndEnabled(): Boolean
}
