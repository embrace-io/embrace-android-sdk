package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.config.remote.Unwinder

public class NativeThreadAnrInterval(

    /**
     * The JVM ID of the sampled thread
     */
    public val id: Long?,

    /**
     * The JVM name of the sampled thread
     */
    public val name: String?,

    /**
     * The priority of the sampled thread
     */
    public val priority: Int?,

    /**
     * The offset in milliseconds that was used to take the native sample
     */
    public val sampleOffsetMs: Long?,

    /**
     * The timestamp in milliseconds at which the monitored thread was first detected as blocked.
     */
    public val threadBlockedTimestamp: Long?,

    /**
     * The stacktrace from the sampled thread.
     */
    public val samples: MutableList<NativeThreadAnrSample>?,

    threadState: ThreadState? = null,
    unwinderType: Unwinder? = null
) {

    /**
     * The stack unwinder used
     */
    public var unwinder: Int? = unwinderType?.code

    /**
     * The JVM state of the sampled thread
     */
    public var state: Int? = threadState?.code
}
