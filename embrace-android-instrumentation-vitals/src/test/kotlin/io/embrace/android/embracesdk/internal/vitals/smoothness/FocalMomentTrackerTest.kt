package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.vitals.fake.FakeVitalsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class FocalMomentTrackerTest {
    private val scheduler = FakeVitalsScheduler()
    private val emitted = mutableListOf<SmoothnessResult>()

    private val tracker = FocalMomentTracker(
        scheduler = scheduler,
        reporter = SmoothnessReporter(emit = emitted::add),
        clock = { 0L },
    )

    // The tracker reads time from SystemClock.uptimeMillis; Robolectric keeps it paused until advance()
    // moves it, so all vsync times are expressed relative to [start], captured before the clock moves.
    private val start = SystemClock.uptimeMillis() * 1_000_000L

    @Test
    fun `after release the aftermath settles at the tighter threshold and emits on flush`() {
        tracker.onInteractionStart()
        assertTrue(scheduler.scheduled)

        redraw(vsyncNanos = start + 16.ms, jankNanos = 4.ms)
        tracker.onInteractionEnd() // finger up -> 100ms threshold

        // 34ms since last activity: under the tight threshold, not settled
        settleAfter(50)
        assertTrue("not settled yet", emitted.isEmpty())
        assertTrue(scheduler.scheduled)

        // 134ms since last activity: past the tight threshold (a held finger would still wait on 500ms)
        settleAfter(100)
        assertTrue("held, not emitted", emitted.isEmpty())
        assertFalse(scheduler.scheduled)

        // detach flushes, dated from start to last activity (16ms in)
        tracker.onScreenStop()
        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(16L, result.durationMs)
    }

    @Test
    fun `a held finger uses the longer grace, not the tight threshold`() {
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16.ms)

        // 134ms since activity: past the tight threshold but the finger is still down, so not settled
        settleAfter(150)
        assertTrue("held finger uses the longer grace", scheduler.scheduled)
        assertTrue(emitted.isEmpty())

        // 534ms since activity: past the held grace
        settleAfter(400)
        assertFalse(scheduler.scheduled)

        tracker.onScreenStop()
        assertEquals(FocalOutcome.SETTLED, emitted.single().outcome)
        assertEquals(16L, emitted.single().durationMs)
    }

    @Test
    fun `a touch move refreshes liveness and keeps a quiescent held focal moment open`() {
        tracker.onInteractionStart()

        // a move 400ms in (no redraws) keeps the finger "live"
        advance(400)
        tracker.onInteractionMove()

        // without the move this would have settled by 500ms; the move pushed the baseline out
        settleAfter(100) // now 500ms; 100ms since the move
        assertTrue("not settled: the move refreshed liveness", scheduler.scheduled)
        assertTrue(emitted.isEmpty())

        // settles 500ms after the move
        settleAfter(400) // now 900ms; 500ms since the move
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `a move after a settle flushes the buffered result and opens a fresh interaction`() {
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16.ms, jankNanos = 4.ms) // a frame so the flush emits

        // no further activity: the held grace elapses and the focal moment enters held
        settleAfter(600)
        assertFalse(scheduler.scheduled)
        assertTrue("held, not yet emitted", emitted.isEmpty())

        // the finger never lifted; resuming the drag flushes the buffered settle and opens a new one
        tracker.onInteractionMove()
        assertEquals(FocalOutcome.SETTLED, emitted.single().outcome)
        assertEquals(16L, emitted.single().durationMs)
        assertTrue("a fresh interaction opened", scheduler.scheduled)
    }

    @Test
    fun `once the finger lifts, the tight threshold applies`() {
        tracker.onInteractionStart()
        tracker.onInteractionEnd()

        // 99ms: one ms short of the tight threshold (a held finger would wait 500ms)
        settleAfter(99)
        assertTrue(scheduler.scheduled)

        // 100ms: at the tight threshold -> held
        settleAfter(1)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `a new screen interrupts the interaction immediately without opening a new focal moment`() {
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16.ms) // a frame so the interrupt emits
        advance(40)
        tracker.onScreenStart()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals(40L, result.durationMs)
        assertFalse("startup opens nothing", scheduler.scheduled)
    }

    @Test
    fun `a continuously redrawing interaction never caps`() {
        tracker.onInteractionStart()

        var t = start + 16.ms
        repeat(400) { // ~6.4s of unbroken frames, well past any settling cap
            redraw(vsyncNanos = t)
            t += 16.ms
        }

        assertTrue("an active interaction has no cap", emitted.isEmpty())
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `pause interrupts an open interaction immediately`() {
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16.ms) // a frame so the interrupt emits
        advance(30)
        tracker.onScreenStop()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals(30L, result.durationMs)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `a slow frame delivered after the settle is still counted, not dropped`() {
        tracker.onInteractionStart()
        tracker.onInteractionEnd() // finger up -> tight threshold
        redraw(vsyncNanos = start + 16.ms) // last known frame

        // The main thread was blocked building a slow frame; the settle timeout elapses before that
        // frame's metrics are delivered.
        settleAfter(200)
        assertFalse("held, not emitted", scheduler.scheduled)
        assertTrue(emitted.isEmpty())

        // the slow frame's metrics finally arrive (contiguous vsync) and wake the held tracker
        redraw(vsyncNanos = start + 20.ms, jankNanos = 8.ms)
        assertTrue("resumed", scheduler.scheduled)

        // it re-settles, and the flush includes the late frame's jank
        scheduler.runPending()
        tracker.onScreenStop()

        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(20L, result.durationMs)
        assertTrue("the late slow frame's jank must be counted", result.normalizedDroppedFrames > 0.0)
    }

    @Test
    fun `an in-flight frame back-dates the start to its render dispatch, but ends at its visible vsync`() {
        tracker.onInteractionStart() // focal moment opens here, at start (the touch-down)
        tracker.onInteractionEnd() // finger up -> tight threshold

        // A frame already being rendered when the user touched: its render was dispatched 30ms before the
        // touch-down and it became visible 10ms after. Its jank is counted in full, so the moment must
        // cover the whole render — back-dated to the dispatch — while ending at the visible vsync.
        redraw(
            vsyncNanos = start + 10.ms, // becomes visible 10ms in
            jankNanos = 8.ms,
            frameDispatchNanos = start - 30.ms, // render dispatched 30ms before the touch
        )

        settleAfter(200)
        assertFalse("held", scheduler.scheduled)

        tracker.onScreenStop()
        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        // start back-dated 30ms to the dispatch, end at the frame's visible vsync 10ms in: 40ms total
        assertEquals("duration spans the render dispatch through the frame's visibility", 40L, result.durationMs)
        // the start epoch shifts back by the same 30ms (clock is fixed at 0), so the end (10ms) stays put
        assertEquals(-30L, result.startTimeMs)
        assertEquals("end time is the visible vsync, unaffected by the back-date", 10L, result.startTimeMs + result.durationMs)
        assertTrue("the in-flight frame's jank is counted", result.normalizedDroppedFrames > 0.0)
    }

    @Test
    fun `a frame after a real idle gap flushes the buffered settle and discards the orphan frame`() {
        tracker.onInteractionStart()
        tracker.onInteractionEnd()
        redraw(vsyncNanos = start + 16.ms)
        settleAfter(120) // past the tight threshold -> held
        assertTrue(emitted.isEmpty())
        assertFalse(scheduler.scheduled)

        // a redraw long after the gap (e.g. an async repaint with no trigger) flushes the settle
        redraw(vsyncNanos = start + 5000.ms, jankNanos = 4.ms)

        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(16L, result.durationMs)
        assertEquals("orphan frame is part of no focal moment", 0.0, result.normalizedDroppedFrames, 0.0)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `frames delivered before a focal moment opens are ignored`() {
        // a large-jank frame before any focal moment: must be dropped, not counted
        redraw(vsyncNanos = start, jankNanos = 1000.ms)
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 10.ms, jankNanos = 5.ms)
        tracker.onScreenStop()

        // only the in-focal-moment frame (~0.3) contributes; the pre-open 1s frame would have added ~60
        assertTrue("pre-open frame ignored", emitted.single().normalizedDroppedFrames < 1.0)
    }

    @Test
    fun `jank is forwarded per frame and normalized into the result`() {
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 1.ms, jankNanos = 1000.ms / 60) // one dropped 60fps frame -> 1.0
        tracker.onScreenStop()

        assertEquals(1.0, emitted.single().normalizedDroppedFrames, 0.01)
    }

    private fun advance(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))

    /** Advances the clock by [millis] and then runs the pending settle check. */
    private fun settleAfter(millis: Long) {
        advance(millis)
        scheduler.runPending()
    }

    /**
     * A frame delivered on the Vitals thread; processed synchronously. [frameDispatchNanos] defaults to
     * the vsync (an instantaneous frame); pass an earlier value to model a frame whose render took time.
     */
    private fun redraw(vsyncNanos: Long, jankNanos: Long = 0L, frameDispatchNanos: Long = vsyncNanos) {
        tracker.onFrame(vsyncNanos, frameDispatchNanos, jankNanos)
    }

    /** Milliseconds expressed in the nanosecond base the tracker works in. */
    private val Int.ms: Long get() = this * 1_000_000L
}
