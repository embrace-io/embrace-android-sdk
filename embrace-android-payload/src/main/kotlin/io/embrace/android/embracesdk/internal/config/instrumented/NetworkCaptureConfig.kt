package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * Declares how the SDK should capture network requests
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object NetworkCaptureConfig {

    /**
     * Sets the default name of the HTTP request header to extract trace ID from.
     */
    const val CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE: String = "x-emb-trace-id"

    /**
     * The network request capture limit per domain
     *
     * sdk_config.networking.default_capture_limit
     */
    fun getRequestLimitPerDomain(): Int = 1000

    /**
     * Declares a Map of domain names to the maximum number of requests
     *
     * sdk_config.networking.domains
     */
    fun getLimitsByDomain(): Map<String, String> = emptyMap()

    /**
     * Declares a list of patterns for requests that should not be captured
     *
     * sdk_config.networking.disabled_url_patterns
     */
    fun getIgnoredRequestPatternList(): List<String> = emptyList()

    /**
     * Declares the key that should be used to capture network request bodies, if any
     *
     * sdk_config.capture_public_key
     */
    fun getNetworkBodyCapturePublicKey(): String? = null
}
