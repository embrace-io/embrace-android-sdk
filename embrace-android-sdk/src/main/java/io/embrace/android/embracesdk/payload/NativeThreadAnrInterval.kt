package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig

internal class NativeThreadAnrInterval(

    /**
     * The JVM ID of the sampled thread
     */
    @SerializedName("id")
    internal val id: Long?,

    /**
     * The JVM name of the sampled thread
     */
    @SerializedName("n")
    internal val name: String?,

    /**
     * The priority of the sampled thread
     */
    @SerializedName("p")
    internal val priority: Int?,

    /**
     * The offset in milliseconds that was used to take the native sample
     */
    @SerializedName("os")
    internal val sampleOffsetMs: Long?,

    /**
     * The timestamp in milliseconds at which the monitored thread was first detected as blocked.
     */
    @SerializedName("t")
    internal val threadBlockedTimestamp: Long?,

    /**
     * The stacktrace from the sampled thread.
     */
    @SerializedName("ss")
    internal val samples: MutableList<NativeThreadAnrSample>?,

    state: ThreadState?,
    unwinder: AnrRemoteConfig.Unwinder?
) {

    /**
     * The stack unwinder used
     */
    @SerializedName("uw")
    internal val unwinder: Int? = unwinder?.code

    /**
     * The JVM state of the sampled thread
     */
    @SerializedName("s")
    internal val state: Int? = state?.code
}
