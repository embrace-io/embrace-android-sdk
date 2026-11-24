package io.embrace.android.embracesdk.internal.clock

/**
 * A raw millisecond time source that returns a primitive, avoiding `() -> Long` boxing.
 */
fun interface TimeSource {
    fun read(): Long
}
