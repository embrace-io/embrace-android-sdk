package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Intervals during which the UI thread was blocked for more than 1 second, which
 * determines that the application is not responding (ANR).
 */
@JsonClass(generateAdapter = true)
internal data class AnrInterval @JvmOverloads constructor(

    /**
     * The time at which the application stopped responding.
     */
    @Json(name = "st")
    val startTime: Long,

    /**
     * The last time the thread was alive.
     */
    @Json(name = "lk")
    val lastKnownTime: Long? = null,

    /**
     * The time the application started responding.
     */
    @Json(name = "en")
    val endTime: Long? = null,

    /**
     * The component of the application which stopped responding.
     */
    @Json(name = "v")
    val type: Type = Type.UI,

    /**
     * The captured stacktraces of the anr interval.
     */
    @Json(name = "se")
    val anrSampleList: AnrSampleList? = null,

    /**
     * The status code of the ANR interval.
     */
    @Json(name = "c")
    val code: Int? = CODE_DEFAULT
) {
    /**
     * The type of thread not responding. Currently only the UI thread is monitored.
     */
    internal enum class Type {
        @Json(name = "ui")
        UI
    }

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLES_CLEARED = 1
    }
}
