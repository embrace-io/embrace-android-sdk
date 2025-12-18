package io.embrace.android.embracesdk.internal.arch.attrs

/**
 * Converts [EmbraceAttribute] to a [Pair]
 */
fun EmbraceAttribute.asPair(): Pair<String, String> = Pair(key.name, value)

/**
 * Creates a new [Pair] based on the key and the passed-in value is coerced into a string via [toString]
 */
fun EmbraceAttributeKey.asPair(value: Any): Pair<String, String> = Pair(name, value.toString())
