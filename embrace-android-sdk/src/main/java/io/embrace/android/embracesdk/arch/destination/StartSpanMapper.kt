package io.embrace.android.embracesdk.arch.destination

/**
 * Converts an object of type T to a [StartSpanData]
 */
internal fun interface StartSpanMapper<T> {
    fun toStartSpanData(obj: T): StartSpanData
}
