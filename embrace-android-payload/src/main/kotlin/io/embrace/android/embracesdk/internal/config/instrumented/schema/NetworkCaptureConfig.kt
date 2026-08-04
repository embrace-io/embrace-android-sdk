package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares how the SDK should capture network requests
 */
@Suppress("FunctionOnlyReturningConstant")
interface NetworkCaptureConfig {

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

    /**
     * Declares the list of hostnames for which traceparent injection and network span forwarding are permitted.
     * If it's null, all hosts are allowed. If it's empty, all hosts are denied.
     * Entries can be exact hostnames or a leading-dot domain suffix that matches all subdomains (e.g. ".test.com" matches "foo.test.com").
     *
     * sdk_config.networking.traceparent_only_allow_domains
     */
    fun getTraceparentOnlyAllowDomains(): List<String>? = null
}
