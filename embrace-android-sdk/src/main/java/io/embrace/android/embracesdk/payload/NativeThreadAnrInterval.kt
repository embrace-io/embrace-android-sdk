package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.internal.config.remote.Unwinder

internal class NativeThreadAnrInterval(

    /**
     * The JVM ID of the sampled thread
     */
    internal val id: Long?,

    /**
     * The JVM name of the sampled thread
     */
    internal val name: String?,

    /**
     * The priority of the sampled thread
     */
    internal val priority: Int?,

    /**
     * The offset in milliseconds that was used to take the native sample
     */
    internal val sampleOffsetMs: Long?,

    /**
     * The timestamp in milliseconds at which the monitored thread was first detected as blocked.
     */
    internal val threadBlockedTimestamp: Long?,

    /**
     * The stacktrace from the sampled thread.
     */
    internal val samples: MutableList<NativeThreadAnrSample>?,

    threadState: ThreadState? = null,
    unwinderType: Unwinder? = null
) {

    /**
     * The stack unwinder used
     */
    internal var unwinder: Int? = unwinderType?.code

    /**
     * The JVM state of the sampled thread
     */
    internal var state: Int? = threadState?.code
}
