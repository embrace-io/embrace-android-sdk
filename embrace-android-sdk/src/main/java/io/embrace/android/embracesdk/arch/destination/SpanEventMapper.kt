package io.embrace.android.embracesdk.arch.destination

/**
 * Converts an object of type T to a [SpanEventData]
 */
internal fun interface SpanEventMapper<T> {
    fun toSpanEventData(obj: T): SpanEventData
}
