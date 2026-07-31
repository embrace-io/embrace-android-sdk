package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * A period during which the target thread did not respond, as known at the moment it is reported.
 *
 * [lastKnownTimeMs] advances while the blockage is in progress and is final once it has ended, so
 * [durationMs] is the duration so far for an ongoing blockage and the total duration for a finished one.
 *
 * Listeners are given this rather than raw timestamps so that they do not need to know how blockages are
 * detected: a change to the threshold or the polling interval arrives as data on the blockage instead of
 * requiring every listener to recalculate what a blockage was.
 *
 * ## Instances are reused
 *
 * A detector reports every callback on one instance, including callbacks that describe different
 * blockages, rather than allocating garbage on each tick of a blocked thread. Its values are therefore
 * only guaranteed to describe the blockage being reported for the duration of the callback. A listener
 * that reads what it needs and returns is unaffected; a listener that retains the blockage beyond the
 * callback must retain [copy] instead, or it will silently observe a later blockage's values.
 */
class ThreadBlockage(
    startTimeMs: Long,
    lastKnownTimeMs: Long,

    /**
     * How long the target thread must be unresponsive before it counts as blocked.
     */
    val thresholdMs: Int,

    /**
     * How often the target thread is checked. Bounds how late a blockage can be observed, so consumers
     * can express that uncertainty rather than having to infer it.
     */
    val pollIntervalMs: Long,
) {

    /**
     * The last time the target thread was known to be responsive, which is when the blockage began.
     */
    var startTimeMs: Long = startTimeMs
        internal set

    /**
     * The most recent time at which the target thread was known to still be blocked.
     */
    var lastKnownTimeMs: Long = lastKnownTimeMs
        internal set

    /**
     * How long the target thread has been blocked for.
     */
    val durationMs: Long get() = lastKnownTimeMs - startTimeMs

    /**
     * Returns a snapshot of this blockage that will not be altered by the detector. Listeners that
     * retain a blockage after their callback returns must retain one of these.
     */
    fun copy(): ThreadBlockage = ThreadBlockage(
        startTimeMs = startTimeMs,
        lastKnownTimeMs = lastKnownTimeMs,
        thresholdMs = thresholdMs,
        pollIntervalMs = pollIntervalMs,
    )
}
