package io.embrace.android.embracesdk.internal.vitals.interaction

import android.os.SystemClock
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import java.util.ArrayDeque

/**
 * Tracks the interaction-latency vital: the time from a user action until the vsync of the frame that renders the app's
 * response. An action whose response draws nothing is dropped.
 *
 * Runs entirely on the Vitals thread. The [emit]ted start time is wall-clock epoch.
 */
internal class InteractionLatencyTracker(
    private val clock: Clock,
    private val emit: (startTimeMs: Long, durationMs: Long) -> Unit,
) {

    private val pending = ArrayDeque<PendingInteraction>()

    /**
     * Signals an action whose input event occurred at [eventTimeMs] ([SystemClock.uptimeMillis]) and whose handlers had
     * finished by [markerNanos] ([System.nanoTime]).
     */
    @WorkerThread
    fun onAction(eventTimeMs: Long, markerNanos: Long) {
        pending.addLast(PendingInteraction(eventTimeMs, markerNanos))
    }

    /**
     * Signals a rendered frame: visible at [vsyncNanos], scheduled for [intendedVsyncNanos] and begun on the main thread at
     * [frameStartNanos], with a frame interval of [frameIntervalNanos].
     */
    @WorkerThread
    fun onFrame(vsyncNanos: Long, frameStartNanos: Long, intendedVsyncNanos: Long, frameIntervalNanos: Long) {
        while (true) {
            val interaction = pending.peekFirst() ?: return
            if (frameStartNanos <= interaction.markerNanos) {
                return
            }
            pending.pollFirst()
            // a frame scheduled more than one interval after the handlers finished was not requested by them
            if (intendedVsyncNanos <= interaction.markerNanos + frameIntervalNanos) {
                complete(interaction.eventTimeMs, vsyncNanos)
            }
        }
    }

    private fun complete(eventTimeMs: Long, vsyncNanos: Long) {
        val durationMs = (vsyncNanos.nanosToMillis() - eventTimeMs).coerceAtLeast(0L)
        val startWallMs = clock.now() - (SystemClock.uptimeMillis() - eventTimeMs)
        emit(startWallMs, durationMs)
    }

    private class PendingInteraction(val eventTimeMs: Long, val markerNanos: Long)
}
