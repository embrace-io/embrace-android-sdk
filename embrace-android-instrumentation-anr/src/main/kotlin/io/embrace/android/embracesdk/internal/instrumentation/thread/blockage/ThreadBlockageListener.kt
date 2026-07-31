package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * Listener for when a thread is blocked for at least a configured interval.
 *
 * Callbacks are delivered on the watchdog thread, never on the blocked thread itself.
 *
 * The same [ThreadBlockage] instance is passed to every callback, and is altered before each one to
 * describe the blockage being reported. Implementations should read the values they need and return.
 * An implementation that keeps hold of a blockage after its callback has returned must keep hold of
 * [ThreadBlockage.copy] instead; keeping the argument itself will silently observe later blockages.
 */
interface ThreadBlockageListener {

    /**
     * Called when a thread has been unresponsive for longer than the threshold.
     */
    fun onBlockageStart(blockage: ThreadBlockage)

    /**
     * Called at regular intervals while a thread remains blocked. Listeners that only care about
     * completed blockages can ignore this.
     */
    fun onBlockageOngoing(blockage: ThreadBlockage) {}

    /**
     * Called when a blocked thread responds again. The blockage is complete, so its duration is final.
     */
    fun onBlockageEnd(blockage: ThreadBlockage)
}
