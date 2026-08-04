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
}

const val DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART: Int = 500
const val DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART: Int = 1500
const val DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART: Int = 2000
