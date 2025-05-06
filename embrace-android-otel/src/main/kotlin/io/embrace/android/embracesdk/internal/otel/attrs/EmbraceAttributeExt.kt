package io.embrace.android.embracesdk.internal.otel.attrs

/**
 * Converts [EmbraceAttribute] to a [Pair]
 */
fun EmbraceAttribute.asPair(): Pair<String, String> = Pair(key.name, value)
