package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface SdkStateApi {

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    public val isStarted: Boolean

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    public fun setAppId(appId: String): Boolean

    public val deviceId: String

    public val currentSessionId: String?

    public fun getLastRunEndState(): Embrace.LastRunEndState
}
