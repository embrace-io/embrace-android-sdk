package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface StartupBehavior {

    /**
     * Controls whether the startup moment is automatically ended.
     */
    public fun isAutomaticEndEnabled(): Boolean
}
