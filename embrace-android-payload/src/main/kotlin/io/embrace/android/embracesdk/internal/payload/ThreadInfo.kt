package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents thread information at a given point in time.
 */
@JsonClass(generateAdapter = true)
data class ThreadInfo(

    /**
     * The thread ID
     */
    val threadId: Long,

    /**
     * Thread state when the ANR is happening [Thread.State]
     */
    val state: Thread.State?,

    /**
     * The name of the thread.
     */
    @Json(name = "n")
    val name: String?,

    /**
     * The priority of the thread
     */
    @Json(name = "p")
    val priority: Int,

    /**
     * String representation of each line of the stack trace.
     */
    @Json(name = "tt")
    val lines: List<String>?
) {

    companion object {

        /**
         * Creates a [ThreadInfo] from the [Thread], [StackTraceElement][] pair,
         * using the thread name and priority, and each stacktrace element as each line with a limited length.
         *
         * @param thread            the exception
         * @param maxStacktraceSize the maximum lines of a stacktrace
         * @return the stacktrace instance
         */
        /**
         * Creates a [ThreadInfo] from the [Thread], [StackTraceElement][] pair,
         * using the thread name and priority, and each stacktrace element as each line.
         *
         * @param thread the exception
         * @return the stacktrace instance
         */
        @JvmStatic
        @JvmOverloads
        fun ofThread(
            thread: Thread,
            stackTraceElements: Array<StackTraceElement>,
            maxStacktraceSize: Int = Integer.MAX_VALUE
        ): ThreadInfo {
            val name = thread.name
            val priority = thread.priority
            val lines = stackTraceElements.take(maxStacktraceSize).map(StackTraceElement::toString)
            return ThreadInfo(thread.id, thread.state, name, priority, lines)
        }
    }
}
