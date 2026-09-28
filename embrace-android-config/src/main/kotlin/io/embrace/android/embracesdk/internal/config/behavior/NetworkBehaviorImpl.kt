package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.PatternCache
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.NetworkConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveNetwork
import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter
import io.embrace.android.embracesdk.internal.network.logging.EmbraceDomainCountLimiter

/**
 * Provides the behavior that functionality relating to network call capture should follow.
 */
class NetworkBehaviorImpl(
    private val config: NetworkConfig,
    private val disabledUrlPatterns: Collection<String>? = null,
) : NetworkBehavior {

    constructor(local: InstrumentedConfig, remote: RemoteConfig?, disabledUrlPatterns: List<String>? = null) :
        this(resolveNetwork(behaviorInputs(local, remote)), disabledUrlPatterns)

    private companion object {
        private val dirtyKeyList = listOf(
            "-----BEGIN PUBLIC KEY-----",
            "-----END PUBLIC KEY-----",
            "\\r",
            "\\n",
            "\\t",
            " ",
        )
    }

    private val patternCache = PatternCache()

    override fun isRequestContentLengthCaptureEnabled(): Boolean = config.requestContentLengthCaptureEnabled

    override fun isOkHttpResponseBodySizeCaptureEnabled(): Boolean = config.okHttpResponseBodySizeCaptureEnabled

    override fun isHttpUrlConnectionCaptureEnabled(): Boolean = config.httpUrlConnectionCaptureEnabled

    override fun isHucLiteInstrumentationEnabled(): Boolean = config.hucLiteInstrumentationEnabled

    override val domainCountLimiter: DomainCountLimiter by lazy {
        EmbraceDomainCountLimiter(
            defaultLimitSupplier = ::getRequestLimitPerDomain,
            domainLimitsSupplier = ::getLimitsByDomain,
        )
    }

    override fun getLimitsByDomain(): Map<String, Int> = config.limitsByDomain

    override fun getRequestLimitPerDomain(): Int = config.requestLimitPerDomain

    override fun getRequestSpanTimeoutMs(): Long = config.requestSpanTimeoutMs

    override fun isUrlEnabled(url: String): Boolean {
        val patterns = disabledUrlPatterns ?: config.disabledUrlPatterns
        return !patternCache.doesStringContainMatchInSet(url, patterns)
    }

    override fun isCaptureBodyEncryptionEnabled(): Boolean =
        getNetworkBodyCapturePublicKey() != null

    override fun getNetworkBodyCapturePublicKey(): String? {
        var keyToClean = config.networkBodyCapturePublicKey
        if (keyToClean != null) {
            for (dirty in dirtyKeyList) {
                keyToClean = keyToClean?.replace(dirty.toRegex(), "")
            }
        }
        return keyToClean
    }

    override fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> = config.networkCaptureRules
}
