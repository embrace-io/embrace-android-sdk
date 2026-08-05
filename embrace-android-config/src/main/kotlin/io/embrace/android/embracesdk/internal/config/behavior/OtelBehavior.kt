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
     * The maximum number of general span events that may be added to a session part span.
     */
    fun getMaxSpanEventsPerSessionPart(): Int

    companion object {

        /**
         * The default limit on non-breadcrumb span events per session part span.
         */
        const val DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART: Int = 1000
    }
}
