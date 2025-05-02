package io.embrace.android.embracesdk.internal.otel.attrs

/**
 * An attribute to be used in telemetry objects and payload envelopes
 */
interface EmbraceAttribute {
    /**
     * The unique name given to the attribute.
     * Don't use this to look up the existence of an attribute in a log or span - use [name] instead
     */
    val id: String

    /**
     * Return the appropriate name for this attribute to be use in the representation of a telemetry object
     */
    val name: String
}
