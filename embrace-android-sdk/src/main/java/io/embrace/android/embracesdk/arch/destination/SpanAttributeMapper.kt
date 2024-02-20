package io.embrace.android.embracesdk.arch.destination

/**
 * Converts an object of type T to a [SpanAttributeData]
 */
internal fun interface SpanAttributeMapper<T> {
    fun T.toSpanAttributeData(): SpanAttributeData
}
