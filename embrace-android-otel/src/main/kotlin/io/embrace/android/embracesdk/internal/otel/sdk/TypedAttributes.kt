package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.payload.toPayloadString
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.attributes.AttributesMutator

/**
 * Sets each attribute via the setter that matches its runtime type. See [setTypedAttribute].
 */
fun AttributesMutator.setTypedAttributes(attributes: Map<String, Any>) {
    attributes.forEach { (key, value) -> setTypedAttribute(key, value) }
}

/**
 * Sets an attribute via the setter that matches its runtime type, so that it reaches OTel exporters typed.
 *
 * An [Int] is widened to a [Long] and a [Float] to a [Double]. Homogeneous lists of those types are set as
 * list attributes. A value of any other type is set as its [toPayloadString].
 */
fun AttributesMutator.setTypedAttribute(key: String, value: Any) {
    when (value) {
        is String -> setStringAttribute(key, value)
        is Boolean -> setBooleanAttribute(key, value)
        is Long -> setLongAttribute(key, value)
        is Int -> setLongAttribute(key, value.toLong())
        is Double -> setDoubleAttribute(key, value)
        is Float -> setDoubleAttribute(key, value.toPayloadDouble())
        is ByteArray -> setByteArrayAttribute(key, value)
        is AnyValue -> setAnyValueAttribute(key, value)
        is List<*> -> setListAttribute(key, value)
        else -> setStringAttribute(key, value.toPayloadString().orEmpty())
    }
}

@Suppress("UNCHECKED_CAST")
private fun AttributesMutator.setListAttribute(key: String, value: List<*>) {
    when {
        value.all { it is String } -> setStringListAttribute(key, value as List<String>)
        value.all { it is Boolean } -> setBooleanListAttribute(key, value as List<Boolean>)
        value.all { it is Long || it is Int } -> setLongListAttribute(key, value.map { (it as Number).toLong() })
        value.all { it is Double || it is Float } -> setDoubleListAttribute(
            key,
            value.map { if (it is Float) it.toPayloadDouble() else it as Double },
        )
        value.all { it is AnyValue } -> setAnyValueAttribute(key, AnyValue.ListValue(value as List<AnyValue>))
        value.all { it is ByteArray } -> setAnyValueAttribute(
            key,
            AnyValue.ListValue(value.map { AnyValue.BytesValue(it as ByteArray) }),
        )
        else -> setStringAttribute(key, value.toPayloadString().orEmpty())
    }
}

/**
 * Widens a [Float] via its decimal representation, so that it stringifies the same as the [Float] did:
 * `1.1f.toDouble()` is `1.100000023841858`, whereas this returns `1.1`.
 */
private fun Float.toPayloadDouble(): Double = toString().toDouble()
