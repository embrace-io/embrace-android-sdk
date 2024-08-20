package io.embrace.android.embracesdk.internal.clock

import java.util.concurrent.TimeUnit

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
public fun Long.millisToNanos(): Long = TimeUnit.MILLISECONDS.toNanos(this)

/**
 * Turns a number that specifies a nanosecond value to milliseconds
 */
public fun Long.nanosToMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)

/**
 * Any epoch timestamp that we detect to be unreasonable to be interpreted as milliseconds, we assume it's an unintended use of nanoseconds
 * based on an old API version or assumption from OpenTelemetry conventions
 */
public fun Long.normalizeTimestampAsMillis(): Long =
    if (this < MAX_MS_CUTOFF) {
        this
    } else {
        this.nanosToMillis()
    }

/**
 * Equivalent to the epoch time of January 1, 5000 12:00:00 AM GMT
 */
internal const val MAX_MS_CUTOFF: Long = 95_617_584_000_000L
