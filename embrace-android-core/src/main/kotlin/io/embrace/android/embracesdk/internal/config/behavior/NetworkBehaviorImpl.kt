package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to network call capture should follow.
 */
public class NetworkBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<SdkLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : NetworkBehavior, MergedConfigBehavior<SdkLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    public companion object {

        /**
         * Sets the default name of the HTTP request header to extract trace ID from.
         */
        public const val CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE: String = "x-emb-trace-id"

        /**
         * Capture request content length by default.
         */
        public const val CAPTURE_REQUEST_CONTENT_LENGTH: Boolean = false

        /**
         * Enable native monitoring by default.
         */
        public const val ENABLE_NATIVE_MONITORING_DEFAULT: Boolean = true
        public const val DEFAULT_NETWORK_CALL_LIMIT: Int = 1000

        private val dirtyKeyList = listOf(
            "-----BEGIN PUBLIC KEY-----",
            "-----END PUBLIC KEY-----",
            "\\r",
            "\\n",
            "\\t",
            " "
        )
    }

    override fun getTraceIdHeader(): String =
        local?.networking?.traceIdHeader ?: CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE

    override fun isRequestContentLengthCaptureEnabled(): Boolean =
        local?.networking?.captureRequestContentLength ?: CAPTURE_REQUEST_CONTENT_LENGTH

    override fun isNativeNetworkingMonitoringEnabled(): Boolean =
        local?.networking?.enableNativeMonitoring ?: ENABLE_NATIVE_MONITORING_DEFAULT

    override fun getNetworkCallLimitsPerDomainSuffix(): Map<String, Int> {
        val limitCeiling = getLimitCeiling()
        val domainSuffixLimits: MutableMap<String, Int> = remote?.networkConfig?.domainLimits?.toMutableMap() ?: mutableMapOf()

        local?.networking?.domains?.forEach { localLimit ->
            val dom = localLimit.domain
            val lim = localLimit.limit
            if (dom != null && lim != null) {
                domainSuffixLimits[dom] = domainSuffixLimits[dom]?.let { remoteLimit ->
                    min(remoteLimit, lim)
                } ?: min(limitCeiling, lim)
            }
        }

        return domainSuffixLimits
    }

    override fun getNetworkCaptureLimit(): Int {
        val remoteDefault = getLimitCeiling()
        return min(remoteDefault, local?.networking?.defaultCaptureLimit ?: remoteDefault)
    }

    override fun isUrlEnabled(url: String): Boolean {
        val patterns =
            remote?.disabledUrlPatterns ?: local?.networking?.disabledUrlPatterns ?: emptySet()
        val regexes = patterns.mapNotNull {
            runCatching { Pattern.compile(it) }.getOrNull()
        }.toSet()
        return regexes.none { it.matcher(url).find() }
    }

    override fun isCaptureBodyEncryptionEnabled(): Boolean = getCapturePublicKey() != null

    override fun getCapturePublicKey(): String? {
        var keyToClean = local?.capturePublicKey
        if (keyToClean != null) {
            for (dirty in dirtyKeyList) {
                keyToClean = keyToClean?.replace(dirty.toRegex(), "")
            }
        }
        return keyToClean
    }

    override fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> = remote?.networkCaptureRules ?: emptySet()

    /**
     * Cap the default limit at whatever the default limit is that is set or implied by the remote config
     */
    private fun getLimitCeiling(): Int = remote?.networkConfig?.defaultCaptureLimit ?: DEFAULT_NETWORK_CALL_LIMIT
}
