package io.embrace.android.embracesdk.payload

import androidx.annotation.CheckResult
import com.google.gson.annotations.SerializedName

/**
 * Intervals during which the UI thread was blocked for more than 1 second, which
 * determines that the application is not responding (ANR).
 */
internal data class AnrInterval @JvmOverloads constructor(

    /**
     * The time at which the application stopped responding.
     */
    @SerializedName("st")
    val startTime: Long,

    /**
     * The last time the thread was alive.
     */
    @SerializedName("lk")
    val lastKnownTime: Long? = null,

    /**
     * The time the application started responding.
     */
    @SerializedName("en")
    val endTime: Long? = null,

    /**
     * The component of the application which stopped responding.
     */
    @SerializedName("v")
    val type: Type = Type.UI,

    /**
     * The captured stacktraces of the anr interval.
     */
    @SerializedName("se")
    val anrSampleList: AnrSampleList? = null,

    /**
     * The status code of the ANR interval.
     */
    @SerializedName("c")
    val code: Int? = CODE_DEFAULT
) {
    /**
     * The type of thread not responding. Currently only the UI thread is monitored.
     */
    internal enum class Type {
        @SerializedName("ui")
        UI
    }

    /**
     * Retrieves the ANR sample count associated with this interval, or 0 if the samples have been
     * redacted.
     */
    fun size(): Int = anrSampleList?.size() ?: 0

    /**
     * Calculates the duration of the interval, returning -1 if this is unknown.
     */
    fun duration(): Long {
        return when (val end = endTime ?: lastKnownTime) {
            null -> -1
            else -> end - startTime
        }
    }

    /**
     * Performs a copy of the AnrInterval that ensures the [anrSampleList] is a new object. Note:
     * that this does not copy all the way down the object tree.
     */
    fun deepCopy(): AnrInterval {
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
    fun clearSamples(): AnrInterval = copy(anrSampleList = null, code = CODE_SAMPLES_CLEARED)

    fun hasSamples(): Boolean = code != CODE_SAMPLES_CLEARED

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLES_CLEARED = 1
    }
}
