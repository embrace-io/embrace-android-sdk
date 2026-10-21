package io.embrace.android.embracesdk.internal.vitals.interaction

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InteractionLatencyTrackerTest {

    private lateinit var emitted: MutableList<Pair<Long, Long>>
    private lateinit var tracker: InteractionLatencyTracker

    @Before
    fun setUp() {
        emitted = mutableListOf()
        tracker = InteractionLatencyTracker(
            clock = { SystemClock.uptimeMillis() + WALL_OFFSET_MS },
            emit = { startTimeMs, durationMs -> emitted.add(startTimeMs - WALL_OFFSET_MS to durationMs) },
        )
    }

    @Test
    fun `a response frame is measured from the input event to its vsync, starting at the event's wall time`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_005.ms)

        frame(vsyncMs = 1_016, startMs = 1_016, intendedMs = 1_016)

        assertEquals(listOf(1_000L to 16L), emitted)
    }

    @Test
    fun `a frame that began before the handlers finished is not the response`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_005.ms)

        frame(vsyncMs = 1_000, startMs = 1_001, intendedMs = 1_000)
        assertTrue(emitted.isEmpty())

        frame(vsyncMs = 1_016, startMs = 1_016, intendedMs = 1_016)
        assertEquals(listOf(1_000L to 16L), emitted)
    }

    @Test
    fun `an action whose response draws nothing is dropped`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_005.ms)

        // the next rendered frame was scheduled long after the handlers finished
        frame(vsyncMs = 3_016, startMs = 3_016, intendedMs = 3_016)
        frame(vsyncMs = 3_033, startMs = 3_033, intendedMs = 3_033)

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `a response scheduled before a blocked main thread freed up is measured to its late vsync`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_300.ms)

        frame(vsyncMs = 1_300, startMs = 1_301, intendedMs = 1_016)

        assertEquals(listOf(1_000L to 300L), emitted)
    }

    @Test
    fun `a response may be scheduled up to one frame interval after the handlers finished`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_005.ms)
        frame(vsyncMs = 1_021, startMs = 1_021, intendedMs = 1_021)

        tracker.onAction(eventTimeMs = 2_000L, markerNanos = 2_005.ms)
        frame(vsyncMs = 2_022, startMs = 2_022, intendedMs = 2_022)

        assertEquals(listOf(1_000L to 21L), emitted)
    }

    @Test
    fun `actions whose handlers finished before the same frame are all measured against it`() {
        tracker.onAction(eventTimeMs = 1_000L, markerNanos = 1_003.ms)
        tracker.onAction(eventTimeMs = 1_008L, markerNanos = 1_010.ms)

        frame(vsyncMs = 1_016, startMs = 1_016, intendedMs = 1_016)

        assertEquals(listOf(1_000L to 16L, 1_008L to 8L), emitted)
    }

    @Test
    fun `a vsync earlier than the event reports zero`() {
        tracker.onAction(eventTimeMs = 1_020L, markerNanos = 1_021.ms)

        frame(vsyncMs = 1_016, startMs = 1_022, intendedMs = 1_016)

        assertEquals(listOf(1_020L to 0L), emitted)
    }

    @Test
    fun `frames with no pending action emit nothing`() {
        frame(vsyncMs = 1_016, startMs = 1_016, intendedMs = 1_016)

        assertTrue(emitted.isEmpty())
    }

    private fun frame(vsyncMs: Int, startMs: Int, intendedMs: Int) =
        tracker.onFrame(
            vsyncNanos = vsyncMs.ms,
            frameStartNanos = startMs.ms,
            intendedVsyncNanos = intendedMs.ms,
            frameIntervalNanos = 16.ms,
        )

    /** Milliseconds expressed in the nanosecond base the tracker works in. */
    private val Int.ms: Long get() = this * 1_000_000L

    private companion object {
        const val WALL_OFFSET_MS = 1_700_000_000_000L
    }
}
