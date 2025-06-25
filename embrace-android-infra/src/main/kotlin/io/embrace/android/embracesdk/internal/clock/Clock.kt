package io.embrace.android.embracesdk.internal.clock

fun interface Clock {

    /**
     * Returns the current milliseconds from epoch.
     */
    fun now(): Long
}
