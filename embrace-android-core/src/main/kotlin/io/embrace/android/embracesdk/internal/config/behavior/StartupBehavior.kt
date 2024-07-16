package io.embrace.android.embracesdk.internal.config.behavior

public interface StartupBehavior {

    /**
     * Controls whether the startup moment is automatically ended.
     */
    public fun isAutomaticEndEnabled(): Boolean
}
