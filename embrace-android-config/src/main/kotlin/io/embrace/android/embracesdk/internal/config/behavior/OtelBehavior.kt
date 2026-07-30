package io.embrace.android.embracesdk.internal.config.behavior

/**
 * Provides the behavior for OpenTelemetry configuration
 */
interface OtelBehavior {

    /**
     * Whether the Kotlin OpenTelemetry SDK should be used instead of the Java one.
     * Returns true if the Kotlin SDK should be used, as configured via local config.
     */
    fun shouldUseKotlinSdk(): Boolean
}
