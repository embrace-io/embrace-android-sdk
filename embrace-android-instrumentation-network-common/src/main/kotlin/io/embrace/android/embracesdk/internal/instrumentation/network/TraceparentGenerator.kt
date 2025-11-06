package io.embrace.android.embracesdk.internal.instrumentation.network

interface TraceparentGenerator {

    /**
     * Generate a valid W3C-compliant traceparent. See the format
     * here: https://www.w3.org/TR/trace-context/#traceparent-header-field-values
     *
     * Note: because Embrace may be recording a span on our side for the given traceparent,
     * we have set the "sampled" flag to indicate that.
     */
    fun generateW3cTraceparent(): String
}
