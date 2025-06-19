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
    val lines: List<String>?,

    /**
     * The total number of frames in the stack before truncation
     */
    @Json(name = "fc")
    val frameCount: Int,
)
