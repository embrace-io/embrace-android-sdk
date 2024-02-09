package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to network call capture should follow.
 */
internal class NetworkBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SdkLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<SdkLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {

        /**
         * Sets the default name of the HTTP request header to extract trace ID from.
         */
        const val CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE = "x-emb-trace-id"

        /**
         * Capture request content length by default.
         */
        const val CAPTURE_REQUEST_CONTENT_LENGTH = false

        /**
         * Enable native monitoring by default.
         */
        const val ENABLE_NATIVE_MONITORING_DEFAULT = true
        const val DEFAULT_NETWORK_CALL_LIMIT = 1000

        private val dirtyKeyList = listOf(
            "-----BEGIN PUBLIC KEY-----",
            "-----END PUBLIC KEY-----",
            "\\r",
            "\\n",
            "\\t",
            " "
        )
    }

    /**
     * The Trace ID Header that can be used to trace a particular request.
     */
    fun getTraceIdHeader(): String =
        local?.networking?.traceIdHeader ?: CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE

    /**
     * Control whether request size for native Android requests is captured.
     */
    fun isRequestContentLengthCaptureEnabled(): Boolean =
        local?.networking?.captureRequestContentLength ?: CAPTURE_REQUEST_CONTENT_LENGTH

    /**
     * Enable the native monitoring.
     */
    fun isNativeNetworkingMonitoringEnabled(): Boolean =
        local?.networking?.enableNativeMonitoring ?: ENABLE_NATIVE_MONITORING_DEFAULT

    /**
     * Map of limits being enforced for each domain suffix for the maximum number of requests that are logged given that suffix. The
     * algorithm to generate the limits for each domain suffix is as follows:
     *
     * - Use the domain-suffix-specific settings defined in the remote config as a base.
     * - For suffixes where there is both local and remote entries, use the local limit if it is smaller than the remote one
     * - For suffixes with only a local entry, apply the local limit or the ceiling defined by the default limit on the remote,
     *   which ever is smaller.
     */
    fun getNetworkCallLimitsPerDomainSuffix(): Map<String, Int> {
        val limitCeiling = getLimitCeiling()
        val domainSuffixLimits: MutableMap<String, Int> = remote?.networkConfig?.domainLimits?.toMutableMap() ?: mutableMapOf()

        local?.networking?.domains?.forEach { localLimit ->
            if (localLimit.domain != null && localLimit.limit != null) {
                domainSuffixLimits[localLimit.domain] =
                    domainSuffixLimits[localLimit.domain]?.let { remoteLimit ->
                        min(remoteLimit, localLimit.limit)
                    } ?: min(limitCeiling, localLimit.limit)
            }
        }

        return domainSuffixLimits
    }

    /**
     * Gets the default limit for network calls for all domains where the limit is not specified.
     */
    fun getNetworkCaptureLimit(): Int {
        val remoteDefault = getLimitCeiling()
        return min(remoteDefault, local?.networking?.defaultCaptureLimit ?: remoteDefault)
    }

    /**
     * Checks if the url is allowed to be reported based on the specified disabled pattern.
     *
     * @param url the url to test
     * @return true if the url is enabled for reporting, false otherwise
     */
    fun isUrlEnabled(url: String): Boolean {
        val patterns =
            remote?.disabledUrlPatterns ?: local?.networking?.disabledUrlPatterns ?: emptySet()
        val regexes = patterns.mapNotNull {
            runCatching { Pattern.compile(it) }.getOrNull()
        }.toSet()
        return regexes.none { it.matcher(url).find() }
    }

    /**
     * Whether network bodies should be captured & encrypted in the payload
     */
    fun isCaptureBodyEncryptionEnabled(): Boolean = getCapturePublicKey() != null

    /**
     * Supplies the public key used for network capture
     */
    fun getCapturePublicKey(): String? {
        var keyToClean = local?.capturePublicKey
        if (keyToClean != null) {
            for (dirty in dirtyKeyList) {
                keyToClean = keyToClean?.replace(dirty.toRegex(), "")
            }
        }
        return keyToClean
    }

    /**
     * Gets the rules for capturing network call bodies
     */
    fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> = remote?.networkCaptureRules ?: emptySet()

    /**
     * Cap the default limit at whatever the default limit is that is set or implied by the remote config
     */
    private fun getLimitCeiling(): Int = remote?.networkConfig?.defaultCaptureLimit ?: DEFAULT_NETWORK_CALL_LIMIT
}
