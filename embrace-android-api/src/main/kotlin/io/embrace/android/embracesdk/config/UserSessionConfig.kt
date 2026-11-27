package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.UserSessionListener

/**
 * Configures how the app observes user sessions.
 */
@SdkConfigDsl
public interface UserSessionConfig {

    /**
     * Registers a listener that is invoked for user session lifecycle events, starting with the user session that is
     * active when the SDK starts, until it is removed with `Embrace.removeUserSessionListener()`. Registering the same
     * listener again has no effect.
     */
    public fun addListener(listener: UserSessionListener)
}
