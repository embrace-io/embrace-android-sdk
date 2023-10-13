package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Holds thread data taken during an [AnrInterval].
 */
internal data class AnrSample(

    /**
     * The timestamp in milliseconds at which this sample was captured
     */
    @SerializedName("ts")
    val timestamp: Long,

    /**
     * All the information for threads that were captured during an ANR sample
     */
    val threads: List<ThreadInfo>?,

    /**
     * The overhead in milliseconds associated with capturing thread traces for this sample
     */
    @SerializedName("o")
    val sampleOverheadMs: Long?,

    /**
     * The status code of the ANR sample.
     */
    @SerializedName("c")
    val code: Int? = CODE_DEFAULT
) {

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLE_LIMIT_REACHED = 1
    }
}
