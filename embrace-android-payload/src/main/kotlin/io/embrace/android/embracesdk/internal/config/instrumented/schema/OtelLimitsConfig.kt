package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares limits for span data.
 */
interface OtelLimitsConfig {

    /**
     * Max number of chars in a span name
     *
     * sdk_config.otel_limits.max_span_name_length
     */
    fun getSpanNameLimit(): Int = 50

    /**
     * Max number of events in a span
     *
     * sdk_config.otel_limits.max_events
     */
    fun getSpanEventLimit(): Int = 10

    /**
     * Max number of attributes
     *
     * sdk_config.otel_limits.max_attributes
     */
    fun getSpanAttributeLimit(): Int = 300

    /**
     * Max number of chars in an attribute key
     *
     * sdk_config.otel_limits.max_attribute_key_length
     */
    fun getSpanAttributeKeyLimit(): Int = 50

    /**
     * Max number of chars in an attribute value
     *
     * sdk_config.otel_limits.max_attribute_value_length
     */
    fun getSpanAttributeValueLimit(): Int = 500
}
