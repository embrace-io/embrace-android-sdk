package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.fakes.FakeDomainCountLimiter
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter

class FakeNetworkBehavior(
    private val captureLimit: Int = 1000,
    private val domains: Map<String, Int> = emptyMap(),
    private val captureHttpUrlConnectionRequests: Boolean = false,
    private val hucLiteInstrumentationEnabled: Boolean = true,
    private val urlEnabled: Boolean = true,
    override val domainCountLimiter: DomainCountLimiter = FakeDomainCountLimiter(),
    val rules: Set<NetworkCaptureRuleRemoteConfig> = emptySet(),
    val captureBodyEncryptionEnabled: Boolean = false,
    val publicKey: String? = null,
) : NetworkBehavior {

    override fun isRequestContentLengthCaptureEnabled(): Boolean = false
    override fun isHttpUrlConnectionCaptureEnabled(): Boolean = captureHttpUrlConnectionRequests
    override fun isHucLiteInstrumentationEnabled(): Boolean = hucLiteInstrumentationEnabled
    override fun getLimitsByDomain(): Map<String, Int> = domains
    override fun getRequestLimitPerDomain(): Int = captureLimit
    override fun isUrlEnabled(url: String): Boolean = urlEnabled
    override fun isCaptureBodyEncryptionEnabled(): Boolean = captureBodyEncryptionEnabled
    override fun getNetworkBodyCapturePublicKey(): String? = publicKey
    override fun getNetworkCaptureRules(): Set<NetworkCaptureRuleRemoteConfig> = rules
}
