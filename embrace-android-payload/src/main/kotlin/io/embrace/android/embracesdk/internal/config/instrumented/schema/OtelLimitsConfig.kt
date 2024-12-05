package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares the limits for OTel data capture.
 *
 * Currently this is not instrumented by the gradle plugin so the values won't change - that will
 * be implemented in a future PR.
 *
 * IMPORTANT NOTE: these functions are only swazzled when the sdk_config.send_data_to_embrace is set to `true`.
 */
interface OtelLimitsConfig {

    /**
     * The maximum length of a span name.
     *
     * sdk_config.otel_limits.max_span_name_length
     */
    fun getMaxNameLength(): Int = 2000

    /**
     * The maximum number of events in a span.
     *
     * sdk_config.otel_limits.max_events
     */
    fun getMaxEventCount(): Int = 11000

    /**
     * The maximum number of attributes in a span.
     *
     * sdk_config.otel_limits.max_attributes
     */
    fun getMaxAttributeCount(): Int = 300

    /**
     * The maximum number of characters in an attribute key.
     *
     * sdk_config.otel_limits.max_attribute_key_length
     */
    fun getMaxAttributeKeyLength(): Int = 1000

    /**
     * The maximum number of characters in an attribute value.
     *
     * sdk_config.otel_limits.max_attribute_value_length
     */
    fun getMaxAttributeValueLength(): Int = 2000
}
