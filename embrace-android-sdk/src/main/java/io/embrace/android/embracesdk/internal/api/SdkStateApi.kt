package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.Embrace

internal interface SdkStateApi {

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    fun isStarted(): Boolean

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    fun setAppId(appId: String): Boolean

    fun getDeviceId(): String

    fun getCurrentSessionId(): String?

    fun getLastRunEndState(): Embrace.LastRunEndState
}
