package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Represents a telemetry type (emb.type). For example, "ux.view" is a type that represents
 * a visual event around a UI element. ux is the type, and view is the subtype. This tells the
 * backend that it can assume the data in the event follows a particular schema.
 */
@InternalApi
public interface TelemetryType : FixedAttribute {
    public val sendImmediately: Boolean
}
