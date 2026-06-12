package io.embrace.android.embracesdk.internal.config.behavior

interface NetworkSpanForwardingBehavior {

    /**
     * Whether network span forwarding is enabled. This is coupled to traceparent injection: NSF is only
     * considered on when injection is also enabled.
     */
    fun isNetworkSpanForwardingEnabled(): Boolean

    /**
     * Whether the network span should be forwarded (i.e. the emb.w3c_traceparent attribute written) for
     * a request to the given host. NSF must be enabled and traceparent injection must apply to the host
     * (injection enabled and the host allowed). Consequently, if injection is off, the attribute is not
     * written even when a traceparent is already present on the request and NSF is on.
     */
    fun shouldForwardForDomain(host: String?): Boolean
}
