package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Provides the behavior that functionality relating to network call capture should follow.
 */
class NetworkBehaviorImpl(
    override val local: InstrumentedConfig,
    override val remote: RemoteConfig?,
    private val disabledUrlPatterns: List<String>? = null,
) : NetworkBehavior {

    companion object {

        const val DEFAULT_NETWORK_CALL_LIMIT: Int = 1000

        private val dirtyKeyList = listOf(
            "-----BEGIN PUBLIC KEY-----",
            "-----END PUBLIC KEY-----",
            "\\r",
            "\\n",
            "\\t",
            " "
        )
    }

    private val cfg = local.networkCapture

    override fun isRequestContentLengthCaptureEnabled(): Boolean =
        local.enabledFeatures.isRequestContentLengthCaptureEnabled()

    override fun isHttpUrlConnectionCaptureEnabled(): Boolean =
        local.enabledFeatures.isHttpUrlConnectionCaptureEnabled()

    override fun getLimitsByDomain(): Map<String, Int> {
        val limits = remote?.networkConfig?.domainLimits ?: cfg.getLimitsByDomain()
            .mapValues { it.value.toInt() }
        val limitCeiling = getRequestLimitPerDomain()

        return limits.mapValues {
            min(it.value, limitCeiling)
        }
    }

    override fun getRequestLimitPerDomain(): Int = min(
        remote?.networkConfig?.defaultCaptureLimit ?: DEFAULT_NETWORK_CALL_LIMIT,
        cfg.getRequestLimitPerDomain()
    )

    override fun isUrlEnabled(url: String): Boolean {
        val patterns = disabledUrlPatterns ?: remote?.disabledUrlPatterns ?: cfg.getIgnoredRequestPatternList()
        val regexes = patterns.mapNotNull {
            runCatching { Pattern.compile(it) }.getOrNull()
        }.toSet()
        return regexes.none { it.matcher(url).find() }
    }

    override fun isCaptureBodyEncryptionEnabled(): Boolean =
        getNetworkBodyCapturePublicKey() != null

    override fun getNetworkBodyCapturePublicKey(): String? {
        var keyToClean = cfg.getNetworkBodyCapturePublicKey()
        if (keyToClean != null) {
            for (dirty in dirtyKeyList) {
                keyToClean = keyToClean?.replace(dirty.toRegex(), "")
            }
        }
        return keyToClean
    }

    override fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> =
        remote?.networkCaptureRules ?: emptySet()
}
