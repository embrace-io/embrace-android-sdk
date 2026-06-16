package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents thread information at a given point in time.
 */
@Serializable
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
    @SerialName("n")
    val name: String?,

    /**
     * The priority of the thread
     */
    @SerialName("p")
    val priority: Int,

    /**
     * String representation of each line of the stack trace.
     */
    @SerialName("tt")
    val lines: List<String>?,

    /**
     * The total number of frames in the stack before truncation
     */
    @SerialName("fc")
    val frameCount: Int,
)
