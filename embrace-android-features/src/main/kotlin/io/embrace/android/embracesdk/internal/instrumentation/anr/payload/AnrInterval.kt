package io.embrace.android.embracesdk.internal.instrumentation.anr.payload

import androidx.annotation.CheckResult

/**
 * Intervals during which the UI thread was blocked for more than 1 second, which
 * determines that the application is not responding (ANR).
 */
data class AnrInterval(

    /**
     * The time at which the application stopped responding.
     */
    val startTime: Long,

    /**
     * The last time the thread was alive.
     */
    val lastKnownTime: Long? = null,

    /**
     * The time the application started responding.
     */
    val endTime: Long? = null,

    /**
     * The component of the application which stopped responding.
     */
    val type: Type = Type.UI,

    /**
     * The captured stacktraces of the anr interval.
     */
    val anrSampleList: AnrSampleList? = null,

    /**
     * The status code of the ANR interval.
     */
    val code: Int? = CODE_DEFAULT,
) {

    /**
     * The type of thread not responding. Currently only the UI thread is monitored.
     */
    enum class Type {
        UI
    }

    companion object {
        const val CODE_DEFAULT: Int = 0
        const val CODE_SAMPLES_CLEARED: Int = 1
    }

    /**
     * Retrieves the ANR sample count associated with this interval, or 0 if the samples have been
     * redacted.
     */
    internal fun size(): Int = anrSampleList?.size() ?: 0

    /**
     * Calculates the duration of the interval, returning -1 if this is unknown.
     */
    internal fun duration(): Long {
        return when (val end = endTime ?: lastKnownTime) {
            null -> -1
            else -> end - startTime
        }
    }

    /**
     * Performs a copy of the AnrInterval that ensures the [anrSampleList] is a new object. Note:
     * that this does not copy all the way down the object tree.
     */
    internal fun deepCopy(): AnrInterval {
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
    internal fun clearSamples(): AnrInterval =
        copy(anrSampleList = null, code = AnrInterval.CODE_SAMPLES_CLEARED)

    internal fun hasSamples(): Boolean = code != AnrInterval.CODE_SAMPLES_CLEARED
}
