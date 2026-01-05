package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * Intervals during which a thread was blocked for more than 1 second.
 */
data class ThreadBlockageInterval(

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
     * The captured stacktraces of the thread blockage interval.
     */
    val samples: List<ThreadBlockageSample>? = null,

    /**
     * The status code of the thread blockage interval.
     */
    val code: Int? = CODE_DEFAULT,
) {

    companion object {
        const val CODE_DEFAULT: Int = 0
        const val CODE_SAMPLES_CLEARED: Int = 1
    }
}
