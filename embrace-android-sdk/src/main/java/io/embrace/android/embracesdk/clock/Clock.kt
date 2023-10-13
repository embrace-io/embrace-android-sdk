package io.embrace.android.embracesdk.clock

internal fun interface Clock {

    /**
     * Returns the current milliseconds from epoch.
     */
    fun now(): Long
}
