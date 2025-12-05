package io.embrace.android.embracesdk.internal.instrumentation.anr.payload

import io.embrace.android.embracesdk.internal.payload.ThreadInfo

/**
 * Holds thread data taken during an [ThreadBlockageInterval].
 */
internal data class ThreadBlockageSample(

    /**
     * The timestamp in milliseconds at which this sample was captured
     */
    val timestamp: Long,

    /**
     * All the information for threads that were captured during a sample
     */
    val threads: List<ThreadInfo>?,

    /**
     * The overhead in milliseconds associated with capturing thread traces for this sample
     */
    val sampleOverheadMs: Long?,

    /**
     * The status code of the sample.
     */
    val code: Int? = CODE_DEFAULT,
) {

    companion object {
        const val CODE_DEFAULT: Int = 0
        const val CODE_SAMPLE_LIMIT_REACHED: Int = 1
    }
}
