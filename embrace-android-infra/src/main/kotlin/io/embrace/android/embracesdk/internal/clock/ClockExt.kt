package io.embrace.android.embracesdk.internal.clock

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Turns a number that specifies a millisecond value to nanoseconds
 */
fun Long.millisToNanos(): Long = TimeUnit.MILLISECONDS.toNanos(this)

/**
 * Turns a number that specifies a nanosecond value to milliseconds
 */
fun Long.nanosToMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)

/**
 * Any epoch timestamp that we detect to be unreasonable to be interpreted as milliseconds, we assume it's an unintended use of nanoseconds
 * based on an old API version or assumption from OpenTelemetry conventions
 */
fun Long.normalizeTimestampAsMillis(): Long =
    if (this < MAX_MS_CUTOFF) {
        this
    } else {
        this.nanosToMillis()
    }

/**
 * Returns the difference between the clock's current time and the current time according to [otherClock] if two back-to-back samples are
 * within 1ms of each other. So if the clock time is 1000L and [otherClock]'s time is 1400L, the return value will be -400L.
 */
fun Clock.offset(otherClock: Clock): Long {
    // To ensure that the offset is the result of clock drift, we take two samples and ensure that their difference is less than 1ms
    // before we use the value. A 1 ms difference between the samples is possible given it could be the result of the time
    // "ticking over" to the next millisecond, but given the calls take the order of microseconds, it should not go beyond that.
    //
    // Any difference that is greater than 1 ms is likely the result of a change to the system clock during this process, or some
    // scheduling quirk that makes the result not trustworthy. In that case, we simply don't return an offset.

    val clockTime1 = now()
    val otherClockTime1 = otherClock.now()
    val clockTime2 = now()
    val otherClockTime2 = otherClock.now()

    val diff1 = clockTime1 - otherClockTime1
    val diff2 = clockTime2 - otherClockTime2

    return if (abs(diff1 - diff2) <= 1L) {
        (diff1 + diff2) / 2
    } else {
        0L
    }
}

/**
 * Equivalent to the epoch time of January 1, 5000 12:00:00 AM GMT
 */
const val MAX_MS_CUTOFF: Long = 95_617_584_000_000L
