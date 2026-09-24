package io.embrace.android.embracesdk.internal.otel.payload

import io.opentelemetry.kotlin.attributes.AnyValue

/**
 * Stringifies an OTel body or attribute value by content, rather than by its [AnyValue] wrapper or array identity.
 */
fun Any.toPayloadString(): String? = when (this) {
    AnyValue.NullValue -> null
    is AnyValue.StringValue -> value
    is AnyValue.BoolValue -> value.toString()
    is AnyValue.LongValue -> value.toString()
    is AnyValue.DoubleValue -> value.toString()
    is AnyValue.BytesValue -> value.contentToString()
    is AnyValue.ListValue -> values.map { it.toPayloadString() }.toString()
    is AnyValue.MapValue -> values.mapValues { it.value.toPayloadString() }.toString()
    is ByteArray -> contentToString()
    else -> toString()
}
