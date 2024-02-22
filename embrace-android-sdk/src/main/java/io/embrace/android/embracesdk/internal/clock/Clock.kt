package io.embrace.android.embracesdk.internal.clock

import io.embrace.android.embracesdk.annotation.InternalApi
import java.util.concurrent.TimeUnit

@InternalApi
public fun interface Clock {

    /**
     * Returns the current milliseconds from epoch.
     */
    public fun now(): Long

    /**
     * Returns the current nanoseconds from epoch
     */
    public fun nowInNanos(): Long = now().millisToNanos()
}

/**
 * Turns a number that specifies a millisecond value to nanoseconds
 */
@InternalApi
public fun Long.millisToNanos(): Long = TimeUnit.MILLISECONDS.toNanos(this)

/**
 * Turns a number that specifies a nanosecond value to milliseconds
 */
@InternalApi
public fun Long.nanosToMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)
