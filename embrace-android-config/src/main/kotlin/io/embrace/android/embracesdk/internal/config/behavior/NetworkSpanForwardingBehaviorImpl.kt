package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.NetworkSpanForwardingConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveNetworkSpanForwarding

class NetworkSpanForwardingBehaviorImpl(
    private val traceparentInjectionBehavior: TraceparentInjectionBehavior,
    private val config: NetworkSpanForwardingConfig,
) : NetworkSpanForwardingBehavior {

    constructor(
        traceparentInjectionBehavior: TraceparentInjectionBehavior,
        thresholdCheck: BehaviorThresholdCheck,
        local: InstrumentedConfig,
        remote: RemoteConfig?,
    ) : this(traceparentInjectionBehavior, resolveNetworkSpanForwarding(behaviorInputs(local, remote, thresholdCheck)))

    companion object {
        /**
         * Header name for the W3C traceparent
         */
        const val TRACEPARENT_HEADER_NAME: String = "traceparent"
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        config.enabled && traceparentInjectionBehavior.isTraceparentInjectionEnabled()

    override fun shouldForwardForDomain(host: String?): Boolean =
        config.enabled && traceparentInjectionBehavior.shouldInjectTraceparent(host)
}
