package io.embrace.android.embracesdk.internal.payload

/**
 * Holds thread data taken during an [AnrInterval].
 */
internal data class AnrSample(

    /**
     * The timestamp in milliseconds at which this sample was captured
     */
    val timestamp: Long,

    /**
     * All the information for threads that were captured during an ANR sample
     */
    val threads: List<ThreadInfo>?,

    /**
     * The overhead in milliseconds associated with capturing thread traces for this sample
     */
    val sampleOverheadMs: Long?,

    /**
     * The status code of the ANR sample.
     */
    val code: Int? = CODE_DEFAULT
) {

    companion object {
        internal const val CODE_DEFAULT = 0
        internal const val CODE_SAMPLE_LIMIT_REACHED = 1
    }
}
