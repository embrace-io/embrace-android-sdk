package io.embrace.android.embracesdk.internal.arch.lifecycle

/**
 * The orders that [ProcessChannels.CrashTeardown] handlers register with, delivered lowest first.
 */
object CrashTeardownOrder {

    /**
     * Stops capturing anything further, ahead of everything that is flushed or persisted.
     */
    const val STOP_CAPTURE: Int = 0

    /**
     * Flushes the logs still held in the sink.
     */
    const val FLUSH: Int = 100

    /**
     * Ends the session part with the crash.
     */
    const val END_SESSION: Int = 200

    /**
     * Marks that a crash happened, for the next launch to find.
     */
    const val MARK: Int = 300

    /**
     * Shuts down payload storage, once everything that stores a payload for the crash has done so.
     */
    const val PERSIST: Int = 400
}
