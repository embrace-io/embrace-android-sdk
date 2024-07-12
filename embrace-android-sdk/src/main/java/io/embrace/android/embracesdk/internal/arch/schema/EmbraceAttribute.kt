package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * An attribute to be used in telemetry objects and payload envelopes
 */
@InternalApi
public interface EmbraceAttribute {

    /**
     * The unique name given to the attribute.
     * Don't use this to look up the existence of an attribute in a log or span - use [name] instead
     */
    public val id: String

    /**
     * Return the appropriate name for this attribute to be use in the representation of a telemetry object
     */
    public val name: String
}
