package io.embrace.android.embracesdk.internal.comms.api

/**
 * Builds the API urls used to connect with Embrace endpoints.
 */
public interface ApiUrlBuilder {

    /**
     * Returns the url used to fetch the config.
     */
    public fun getConfigUrl(): String

    /**
     * Returns the url used to send data to Embrace.
     */
    public fun getEmbraceUrlWithSuffix(apiVersion: String, suffix: String): String
}
