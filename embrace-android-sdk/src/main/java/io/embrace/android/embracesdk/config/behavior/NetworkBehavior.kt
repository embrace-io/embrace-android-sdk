package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to network call capture should follow.
 */
internal class NetworkBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> SdkLocalConfig?,
    remoteSupplier: () -> RemoteConfig?
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
     * List of domains to be limited for tracking.
     */
    fun getNetworkCallLimitsPerDomain(): Map<String, Int> {
        val limitCeiling = getLimitCeiling()
        val domainLimits: MutableMap<String, Int> = remote?.networkConfig?.domainLimits?.toMutableMap() ?: mutableMapOf()

        local?.networking?.domains?.forEach { localDomainLimit ->
            if (localDomainLimit.domain != null && localDomainLimit.limit != null) {
                domainLimits[localDomainLimit.domain] =
                    domainLimits[localDomainLimit.domain]?.let { remoteLimit ->
                        min(remoteLimit, localDomainLimit.limit)
                    } ?: min(limitCeiling, localDomainLimit.limit)
            }
        }

        return domainLimits
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
     * Cap the limit at the default limit set on the remote config
     */
    private fun getLimitCeiling(): Int = remote?.networkConfig?.defaultCaptureLimit ?: DEFAULT_NETWORK_CALL_LIMIT
}
