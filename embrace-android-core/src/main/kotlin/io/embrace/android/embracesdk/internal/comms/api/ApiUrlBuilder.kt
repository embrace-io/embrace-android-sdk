package io.embrace.android.embracesdk.internal.comms.api

/**
 * Builds the API urls used to connect with Embrace endpoints.
 */
interface ApiUrlBuilder {

    /**
     * The App ID that will be used in API requests.
     */
    val appId: String

    /**
     * The Device ID that will be used in API requests.
     */
    val deviceId: String

    /**
     * Base URL for the data endpoint
     */
    val baseDataUrl: String

    /**
     * Returns the url used to send data to Embrace.
     */
    fun resolveUrl(endpoint: Endpoint): String
}
