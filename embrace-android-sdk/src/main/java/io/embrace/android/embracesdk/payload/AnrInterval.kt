package io.embrace.android.embracesdk.payload

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

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLES_CLEARED = 1
    }
}
