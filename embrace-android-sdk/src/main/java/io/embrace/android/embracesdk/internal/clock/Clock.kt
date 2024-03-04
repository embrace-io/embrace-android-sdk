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
internal fun Long.millisToNanos(): Long = TimeUnit.MILLISECONDS.toNanos(this)

/**
 * Turns a number that specifies a nanosecond value to milliseconds
 */
internal fun Long.nanosToMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)

/**
 * Any epoch timestamp that we detect to be unreasonable to be interpreted as milliseconds, we assume it's an unintended use of nanoseconds
 * based on an old API version or assumption from OpenTelemetry conventions
 */
internal fun Long.normalizeTimestampAsMillis(): Long =
    if (this < MAX_MS_CUTOFF) {
        this
    } else {
        this.nanosToMillis()
    }

/**
 * Equivalent to the epoch time of January 1, 5000 12:00:00 AM GMT
 */
internal const val MAX_MS_CUTOFF = 95_617_584_000_000L
