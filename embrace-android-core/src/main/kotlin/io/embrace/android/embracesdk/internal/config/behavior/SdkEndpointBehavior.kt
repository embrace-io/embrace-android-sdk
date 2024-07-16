package io.embrace.android.embracesdk.internal.config.behavior

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
