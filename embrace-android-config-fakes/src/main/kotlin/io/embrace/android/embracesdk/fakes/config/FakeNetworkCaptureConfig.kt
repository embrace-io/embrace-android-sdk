package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.NetworkCaptureConfig

class FakeNetworkCaptureConfig(
    base: NetworkCaptureConfig = InstrumentedConfigImpl.networkCapture,
    private val requestLimit: Int = base.getRequestLimitPerDomain(),
    private val limits: Map<String, String> = base.getLimitsByDomain(),
    private val ignoredRequestPatterns: List<String> = base.getIgnoredRequestPatternList(),
    private val publicKey: String? = base.getNetworkBodyCapturePublicKey(),
    private val traceparentOnlyAllowDomains: List<String>? = base.getTraceparentOnlyAllowDomains(),
) : NetworkCaptureConfig {
    override fun getRequestLimitPerDomain(): Int = requestLimit
    override fun getLimitsByDomain(): Map<String, String> = limits
    override fun getIgnoredRequestPatternList(): List<String> = ignoredRequestPatterns
    override fun getNetworkBodyCapturePublicKey(): String? = publicKey
    override fun getTraceparentOnlyAllowDomains(): List<String>? = traceparentOnlyAllowDomains
}
