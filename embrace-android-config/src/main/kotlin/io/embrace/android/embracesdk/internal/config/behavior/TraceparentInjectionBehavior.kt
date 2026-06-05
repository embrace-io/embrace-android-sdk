package io.embrace.android.embracesdk.internal.config.behavior

interface TraceparentInjectionBehavior {

    /**
     * Whether W3C traceparent injection is enabled.
     */
    fun isTraceparentInjectionEnabled(): Boolean

    /**
     * Whether the SDK should inject the network span's traceparent on a request to the given host when one is not already present.
     */
    fun shouldInjectTraceparent(host: String?): Boolean
}
