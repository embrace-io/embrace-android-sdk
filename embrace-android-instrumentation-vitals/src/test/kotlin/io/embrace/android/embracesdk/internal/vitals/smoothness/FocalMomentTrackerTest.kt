package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class FocalMomentTrackerTest {

    /** Runs posted (hopped) work synchronously and lets the test fire the single pending settle. */
    private class FakeScheduler : VitalsScheduler {
        private var pending: Runnable? = null
        val settleArmed: Boolean get() = pending != null
        override fun post(action: Runnable) = action.run()
        override fun scheduleSettle(delayMs: Long, action: Runnable) { pending = action }
        override fun cancelSettle() { pending = null }
        fun fireSettle() = pending?.run() ?: Unit
    }

    private val scheduler = FakeScheduler()
    private val emitted = mutableListOf<SmoothnessResult>()

    private val tracker = FocalMomentTracker(
        scheduler = scheduler,
        reporter = SmoothnessReporter(emit = emitted::add),
        clock = Clock { 0L },
    )

    // The tracker reads time from SystemClock.uptimeMillis; Robolectric keeps it paused until advance()
    // moves it, so vsync times are expressed relative to nowNanos().
    private fun nowNanos(): Long = SystemClock.uptimeMillis() * 1_000_000L
    private fun advance(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))

    /** A frame delivered on the Vitals thread; processed synchronously. */
    private fun redraw(vsyncNanos: Long, jankNanos: Long = 0L) {
        tracker.onFrame(vsyncNanos, jankNanos)
    }

    @Test
    fun `after release the aftermath settles at the tighter threshold and emits on flush`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        assertTrue(scheduler.settleArmed)

        redraw(vsyncNanos = start + 16_000_000L, jankNanos = 4_000_000L)
        tracker.onInteractionEnd() // finger up -> 100ms threshold

        // 34ms since last activity: under the tight threshold, not settled
        advance(50)
        scheduler.fireSettle()
        assertTrue("not settled yet", emitted.isEmpty())
        assertTrue(scheduler.settleArmed)

        // 134ms since last activity: past the tight threshold (a held finger would still wait on 500ms)
        advance(100)
        scheduler.fireSettle()
        assertTrue("held, not emitted", emitted.isEmpty())
        assertFalse(scheduler.settleArmed)

        // detach flushes, dated from start to last activity (16ms in)
        tracker.onScreenStop()
        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(16L, result.durationMs)
    }

    @Test
    fun `a held finger uses the longer grace, not the tight threshold`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16_000_000L)

        // 134ms since activity: past the tight threshold but the finger is still down, so not settled
        advance(150)
        scheduler.fireSettle()
        assertTrue("held finger uses the longer grace", scheduler.settleArmed)
        assertTrue(emitted.isEmpty())

        // 534ms since activity: past the held grace
        advance(400)
        scheduler.fireSettle()
        assertFalse(scheduler.settleArmed)

        tracker.onScreenStop()
        assertEquals(FocalOutcome.SETTLED, emitted.single().outcome)
        assertEquals(16L, emitted.single().durationMs)
    }

    @Test
    fun `a touch move refreshes liveness and keeps a quiescent held focal moment open`() {
        val start = nowNanos()
        tracker.onInteractionStart()

        // a move 400ms in (no redraws) keeps the finger "live"
        advance(400)
        tracker.onInteractionMove()

        // without the move this would have settled by 500ms; the move pushed the baseline out
        advance(100) // now 500ms; 100ms since the move
        scheduler.fireSettle()
        assertTrue("not settled: the move refreshed liveness", scheduler.settleArmed)
        assertTrue(emitted.isEmpty())

        // settles 500ms after the move
        advance(400) // now 900ms; 500ms since the move
        scheduler.fireSettle()
        assertFalse(scheduler.settleArmed)
    }

    @Test
    fun `a move after a settle flushes the buffered result and opens a fresh interaction`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16_000_000L, jankNanos = 4_000_000L) // a frame so the flush emits

        // no further activity: the held grace elapses and the focal moment enters held
        advance(600)
        scheduler.fireSettle()
        assertFalse(scheduler.settleArmed)
        assertTrue("held, not yet emitted", emitted.isEmpty())

        // the finger never lifted; resuming the drag flushes the buffered settle and opens a new one
        tracker.onInteractionMove()
        assertEquals(FocalOutcome.SETTLED, emitted.single().outcome)
        assertEquals(16L, emitted.single().durationMs)
        assertTrue("a fresh interaction opened", scheduler.settleArmed)
    }

    @Test
    fun `once the finger lifts, the tight threshold applies`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        tracker.onInteractionEnd()

        // 99ms: one ms short of the tight threshold (a held finger would wait 500ms)
        advance(99)
        scheduler.fireSettle()
        assertTrue(scheduler.settleArmed)

        // 100ms: at the tight threshold -> held
        advance(1)
        scheduler.fireSettle()
        assertFalse(scheduler.settleArmed)
    }

    @Test
    fun `a new screen interrupts the interaction immediately without opening a new focal moment`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16_000_000L) // a frame so the interrupt emits
        advance(40)
        tracker.onScreenStart()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals(40L, result.durationMs)
        assertFalse("startup opens nothing", scheduler.settleArmed)
    }

    @Test
    fun `a continuously redrawing interaction never caps`() {
        val start = nowNanos()
        tracker.onInteractionStart()

        var t = start + 16_000_000L
        repeat(400) { // ~6.4s of unbroken frames, well past any settling cap
            redraw(vsyncNanos = t)
            t += 16_000_000L
        }

        assertTrue("an active interaction has no cap", emitted.isEmpty())
        assertTrue(scheduler.settleArmed)
    }

    @Test
    fun `pause interrupts an open interaction immediately`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 16_000_000L) // a frame so the interrupt emits
        advance(30)
        tracker.onScreenStop()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals(30L, result.durationMs)
        assertFalse(scheduler.settleArmed)
    }

    @Test
    fun `a slow frame delivered after the settle is still counted, not dropped`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        tracker.onInteractionEnd() // finger up -> tight threshold
        redraw(vsyncNanos = start + 16_000_000L) // last known frame

        // The main thread was blocked building a slow frame; the settle timeout fires before that
        // frame's metrics are delivered.
        advance(200)
        scheduler.fireSettle()
        assertFalse("held, not emitted", scheduler.settleArmed)
        assertTrue(emitted.isEmpty())

        // the slow frame's metrics finally arrive (contiguous vsync) and wake the held tracker
        tracker.onFrame(vsyncNanos = start + 20_000_000L, jankNanos = 8_000_000L)
        assertTrue("resumed", scheduler.settleArmed)

        // it re-settles, and the flush includes the late frame's jank
        scheduler.fireSettle()
        tracker.onScreenStop()

        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(20L, result.durationMs)
        assertTrue("the late slow frame's jank must be counted", result.normalizedDroppedFrames > 0.0)
    }

    @Test
    fun `a frame after a real idle gap flushes the buffered settle and discards the orphan frame`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        tracker.onInteractionEnd()
        redraw(vsyncNanos = start + 16_000_000L)
        advance(120) // past the tight threshold -> held
        scheduler.fireSettle()
        assertTrue(emitted.isEmpty())
        assertFalse(scheduler.settleArmed)

        // a redraw long after the gap (e.g. an async repaint with no trigger) flushes the settle
        tracker.onFrame(vsyncNanos = start + 5_000_000_000L, jankNanos = 4_000_000L)

        val result = emitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(16L, result.durationMs)
        assertEquals("orphan frame is part of no focal moment", 0.0, result.normalizedDroppedFrames, 0.0)
        assertFalse(scheduler.settleArmed)
    }

    @Test
    fun `frames delivered before a focal moment opens are ignored`() {
        val start = nowNanos()
        // a large-jank frame before any focal moment: must be dropped, not counted
        tracker.onFrame(vsyncNanos = start, jankNanos = 1_000_000_000L)
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 10_000_000L, jankNanos = 5_000_000L)
        tracker.onScreenStop()

        // only the in-focal-moment frame (~0.3) contributes; the pre-open 1s frame would have added ~60
        assertTrue("pre-open frame ignored", emitted.single().normalizedDroppedFrames < 1.0)
    }

    @Test
    fun `jank is forwarded per frame and normalized into the result`() {
        val start = nowNanos()
        tracker.onInteractionStart()
        redraw(vsyncNanos = start + 1_000_000L, jankNanos = 1_000_000_000L / 60) // one dropped 60fps frame -> 1.0
        tracker.onScreenStop()

        assertEquals(1.0, emitted.single().normalizedDroppedFrames, 0.01)
    }
}
