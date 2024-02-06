package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig

@JsonClass(generateAdapter = true)
internal class NativeThreadAnrInterval(

    /**
     * The JVM ID of the sampled thread
     */
    @Json(name = "id")
    internal val id: Long?,

    /**
     * The JVM name of the sampled thread
     */
    @Json(name = "n")
    internal val name: String?,

    /**
     * The priority of the sampled thread
     */
    @Json(name = "p")
    internal val priority: Int?,

    /**
     * The offset in milliseconds that was used to take the native sample
     */
    @Json(name = "os")
    internal val sampleOffsetMs: Long?,

    /**
     * The timestamp in milliseconds at which the monitored thread was first detected as blocked.
     */
    @Json(name = "t")
    internal val threadBlockedTimestamp: Long?,

    /**
     * The stacktrace from the sampled thread.
     */
    @Json(name = "ss")
    internal val samples: MutableList<NativeThreadAnrSample>?,

    threadState: ThreadState? = null,
    unwinderType: AnrRemoteConfig.Unwinder? = null
) {

    /**
     * The stack unwinder used
     */
    @Json(name = "uw")
    internal var unwinder: Int? = unwinderType?.code

    /**
     * The JVM state of the sampled thread
     */
    @Json(name = "s")
    internal var state: Int? = threadState?.code
}
