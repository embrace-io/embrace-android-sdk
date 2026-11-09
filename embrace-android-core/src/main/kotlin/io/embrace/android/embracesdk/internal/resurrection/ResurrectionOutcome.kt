package io.embrace.android.embracesdk.internal.resurrection

/**
 * How a pass of [PayloadResurrectionService.resurrectOldPayloads] ended, which decides whether payloads recorded since can be
 * delivered without overtaking the ones cached by previous processes.
 */
enum class ResurrectionOutcome {

    /**
     * Every cached payload was handled, or the pass failed partway. Payloads recorded since can be delivered: a failure is not
     * allowed to hold up delivery for the rest of the process.
     */
    COMPLETE,

    /**
     * The intake refused a payload, which it does only once the SDK is shutting down, after a crash or when it is disabled or
     * closed. The pass stopped there, leaving that payload and the ones after it on disk for the next launch to resurrect before
     * it sends anything newer, so nothing newer may be delivered in this process.
     */
    INTERRUPTED,
}
