package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Holds thread data taken during an [AnrInterval].
 */
@JsonClass(generateAdapter = true)
internal data class AnrSample(

    /**
     * The timestamp in milliseconds at which this sample was captured
     */
    @Json(name = "ts")
    val timestamp: Long,

    /**
     * All the information for threads that were captured during an ANR sample
     */
    val threads: List<ThreadInfo>?,

    /**
     * The overhead in milliseconds associated with capturing thread traces for this sample
     */
    @Json(name = "o")
    val sampleOverheadMs: Long?,

    /**
     * The status code of the ANR sample.
     */
    @Json(name = "c")
    val code: Int? = CODE_DEFAULT
) {

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLE_LIMIT_REACHED = 1
    }
}
