package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface SdkEndpointBehavior {

    /**
     * Data base URL.
     */
    public fun getData(appId: String?): String

    /**
     * Config base URL.
     */
    public fun getConfig(appId: String?): String
}
