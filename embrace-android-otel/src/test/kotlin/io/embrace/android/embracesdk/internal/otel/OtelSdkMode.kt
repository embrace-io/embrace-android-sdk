package io.embrace.android.embracesdk.internal.otel

/**
 * The opentelemetry-kotlin implementation a unit test runs against.
 */
internal enum class OtelSdkMode(val useKotlinSdk: Boolean) {

    /**
     * opentelemetry-kotlin's 'compat' implementation, which wraps opentelemetry-java.
     */
    COMPAT(false),

    /**
     * opentelemetry-kotlin's 'regular' implementation, written in pure Kotlin.
     */
    REGULAR(true),
    ;

    companion object {
        fun parameters(): List<Array<Any>> = entries.map { arrayOf(it) }
    }
}
