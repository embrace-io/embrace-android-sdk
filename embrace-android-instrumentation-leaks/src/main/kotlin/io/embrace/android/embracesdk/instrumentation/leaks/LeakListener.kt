package io.embrace.android.embracesdk.instrumentation.leaks

/**
 * Receives references that [LeakDetector] has concluded are probably leaked.
 */
internal fun interface LeakListener {

    /**
     * Invoked with a [referent] that was still reachable when the sentinel allocated alongside it was reclaimed. [trackedAtMs]
     * is when the lifecycle of [referent] ended, and [token] is the value passed to [LeakDetector.trackClosed].
     *
     * Implementations must not retain [referent] beyond this call, and should avoid allocating so that leaks can be recorded
     * in low-memory conditions.
     */
    fun onLeakDetected(referent: Any, trackedAtMs: Long, token: Any?)
}
