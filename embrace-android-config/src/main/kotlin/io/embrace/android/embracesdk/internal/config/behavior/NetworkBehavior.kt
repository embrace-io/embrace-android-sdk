package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter

interface NetworkBehavior {

    /**
     * Control whether request size for native Android requests is captured.
     */
    fun isRequestContentLengthCaptureEnabled(): Boolean

    /**
     * Control whether the size of an OkHttp response body is captured when the response has no
     * Content-Length header. Doing so requires buffering the whole body into memory, so it is
     * disabled by default to avoid excessive heap usage on large/streaming responses.
     */
    fun isOkHttpResponseBodySizeCaptureEnabled(): Boolean

    /**
     * Enable the native monitoring.
     */
    fun isHttpUrlConnectionCaptureEnabled(): Boolean

    /**
     * Enable HUC Lite instrumentation.
     */
    fun isHucLiteInstrumentationEnabled(): Boolean

    /**
     * Map of limits being enforced for each domain suffix for the maximum number of requests that are logged given that suffix. The
     * algorithm to generate the limits for each domain suffix is as follows:
     *
     * - Use the domain-suffix-specific settings defined in the remote config as a base.
     * - For suffixes where there is both local and remote entries, use the local limit if it is smaller than the remote one
     * - For suffixes with only a local entry, apply the local limit or the ceiling defined by the default limit on the remote,
     *   which ever is smaller.
     */
    fun getLimitsByDomain(): Map<String, Int>

    /**
     * Gets the default limit for network calls for all domains where the limit is not specified.
     */
    fun getRequestLimitPerDomain(): Int

    /**
     * The fallback duration in milliseconds after which an in-flight network request span is assumed
     * to have leaked and is dropped, used when the HTTP client exposes no call-level timeout of its own.
     */
    fun getRequestSpanTimeoutMs(): Long

    /**
     * Checks if the url is allowed to be reported based on the specified disabled pattern.
     *
     * @param url the url to test
     * @return true if the url is enabled for reporting, false otherwise
     */
    fun isUrlEnabled(url: String): Boolean

    /**
     * Whether network bodies should be captured & encrypted in the payload
     */
    fun isCaptureBodyEncryptionEnabled(): Boolean

    /**
     * Supplies the public key used for network capture
     */
    fun getNetworkBodyCapturePublicKey(): String?

    /**
     * Gets the rules for capturing network call bodies
     */
    fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig>

    /**
     * Domain count limiter for network requests
     */
    val domainCountLimiter: DomainCountLimiter
}
