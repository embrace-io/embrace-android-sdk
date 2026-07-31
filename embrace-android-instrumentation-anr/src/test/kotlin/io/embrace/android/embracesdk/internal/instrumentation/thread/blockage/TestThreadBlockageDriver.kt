package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

/**
 * Drives a [ThreadBlockageListener] with raw timestamps, performing the same bookkeeping that
 * [BlockedThreadDetector] does in production: a blockage starts at the last time the target thread was
 * known to be responsive, every subsequent callback for that blockage carries the same start time, and
 * one reused [ThreadBlockage] instance describes them all.
 */
internal class TestThreadBlockageDriver(
    private val listener: ThreadBlockageListener,
    thresholdMs: Int = 1000,
    pollIntervalMs: Long = 100,
) {

    private val blockage = ThreadBlockage(
        startTimeMs = 0,
        lastKnownTimeMs = 0,
        thresholdMs = thresholdMs,
        pollIntervalMs = pollIntervalMs,
    )

    fun start(timeMs: Long) {
        blockage.startTimeMs = timeMs
        listener.onBlockageStart(blockageAt(timeMs))
    }

    fun ongoing(timeMs: Long) {
        listener.onBlockageOngoing(blockageAt(timeMs))
    }

    fun end(timeMs: Long) {
        listener.onBlockageEnd(blockageAt(timeMs))
    }

    private fun blockageAt(timeMs: Long): ThreadBlockage {
        blockage.lastKnownTimeMs = timeMs
        return blockage
    }
}
