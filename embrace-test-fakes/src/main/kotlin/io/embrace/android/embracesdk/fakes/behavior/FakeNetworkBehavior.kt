package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.NetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig

class FakeNetworkBehavior(
    private val captureLimit: Int = 1000,
    private val domains: Map<String, Int> = emptyMap()
) : NetworkBehavior {
    override fun getTraceIdHeader(): String = NetworkCaptureConfig.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
    override fun isRequestContentLengthCaptureEnabled(): Boolean = false
    override fun isHttpUrlConnectionCaptureEnabled(): Boolean = true
    override fun getLimitsByDomain(): Map<String, Int> = domains
    override fun getRequestLimitPerDomain(): Int = captureLimit
    override fun isUrlEnabled(url: String): Boolean = true
    override fun isCaptureBodyEncryptionEnabled(): Boolean = false
    override fun getNetworkBodyCapturePublicKey(): String? = null
    override fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> = emptySet()
}
