package io.embrace.android.embracesdk.internal.config.behavior

interface SdkEndpointBehavior {

    /**
     * Data base URL.
     */
    fun getData(appId: String?): String

    /**
     * Config base URL.
     */
    fun getConfig(appId: String?): String
}
