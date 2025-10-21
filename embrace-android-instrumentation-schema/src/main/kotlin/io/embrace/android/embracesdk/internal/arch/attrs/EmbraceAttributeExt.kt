package io.embrace.android.embracesdk.internal.arch.attrs

/**
 * Converts [EmbraceAttribute] to a [Pair]
 */
fun EmbraceAttribute.asPair(): Pair<String, String> = Pair(key.name, value)
