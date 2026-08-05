package io.embrace.android.embracesdk.internal.config.behavior

/**
 * Provides the behavior for OpenTelemetry configuration
 */
interface OtelBehavior {

    /**
     * Whether the Kotlin OpenTelemetry SDK should be used instead of the Java one.
     * Returns true if the Kotlin SDK should be used, false if it was disabled via remote config.
     */
    fun shouldUseKotlinSdk(): Boolean

    /**
     * The maximum number of spans created via the public API that can be recorded in a session part.
     */
    fun getMaxCustomSpansPerSessionPart(): Int

    /**
     * The maximum number of spans created by the SDK's own instrumentation, other than network request spans,
     * that can be recorded in a session part.
     */
    fun getMaxInternalSpansPerSessionPart(): Int

    /**
     * The maximum number of network request spans that can be recorded in a session part.
     */
    fun getMaxNetworkSpansPerSessionPart(): Int

    /**
     * The maximum number of general span events that may be added to a session part span.
     */
    fun getMaxSpanEventsPerSessionPart(): Int

    /**
     * The interval in milliseconds between periodic caching of the in-progress session part to disk.
     * This is clamped to the range [MIN_PERIODIC_CACHE_INTERVAL_MS]..[MAX_PERIODIC_CACHE_INTERVAL_MS].
     */
    fun getPeriodicCacheIntervalMs(): Long

    companion object {

        /**
         * The default limit on non-breadcrumb span events per session part span.
         */
        const val DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART: Int = 1000
    }
}

const val DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART: Int = 500
const val DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART: Int = 1500
const val DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART: Int = 2000
const val DEFAULT_PERIODIC_CACHE_INTERVAL_MS: Long = 2000L
const val MIN_PERIODIC_CACHE_INTERVAL_MS: Long = 2000L
const val MAX_PERIODIC_CACHE_INTERVAL_MS: Long = 120000L
