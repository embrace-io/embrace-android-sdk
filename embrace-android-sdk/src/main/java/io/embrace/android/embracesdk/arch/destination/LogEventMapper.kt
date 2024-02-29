package io.embrace.android.embracesdk.arch.destination

/**
 * Converts an object of type T to a [LogEventData]
 */
internal fun interface LogEventMapper<T> {
    fun toLogEventData(obj: T): LogEventData
}
