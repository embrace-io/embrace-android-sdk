package io.embrace.android.embracesdk.internal.payload.extensions

import androidx.annotation.CheckResult
import io.embrace.android.embracesdk.internal.payload.AnrInterval

/**
 * Retrieves the ANR sample count associated with this interval, or 0 if the samples have been
 * redacted.
 */
internal fun AnrInterval.size(): Int = anrSampleList?.size() ?: 0

/**
 * Calculates the duration of the interval, returning -1 if this is unknown.
 */
internal fun AnrInterval.duration(): Long {
    return when (val end = endTime ?: lastKnownTime) {
        null -> -1
        else -> end - startTime
    }
}

/**
 * Performs a copy of the AnrInterval that ensures the [anrSampleList] is a new object. Note:
 * that this does not copy all the way down the object tree.
 */
internal fun AnrInterval.deepCopy(): AnrInterval {
    val copy = when (val original = anrSampleList) {
        null -> null
        else -> original.copy(samples = original.samples.toMutableList())
    }
    return AnrInterval(
        startTime,
        lastKnownTime,
        endTime,
        type,
        copy,
        code
    )
}

@CheckResult
internal fun AnrInterval.clearSamples(): AnrInterval = copy(anrSampleList = null, code = AnrInterval.CODE_SAMPLES_CLEARED)

internal fun AnrInterval.hasSamples(): Boolean = code != AnrInterval.CODE_SAMPLES_CLEARED
