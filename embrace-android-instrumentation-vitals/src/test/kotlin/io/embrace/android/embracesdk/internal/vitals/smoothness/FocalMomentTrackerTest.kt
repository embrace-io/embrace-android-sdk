package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.vitals.FrameCounts
import io.embrace.android.embracesdk.internal.vitals.MainThreadProbe
import io.embrace.android.embracesdk.internal.vitals.counts
import io.embrace.android.embracesdk.internal.vitals.fake.FakeVitalsScheduler
import io.embrace.android.embracesdk.internal.vitals.responsiveness.InteractionResponsivenessReporter
import io.embrace.android.embracesdk.internal.vitals.responsiveness.InteractionResponsivenessResult
import io.embrace.android.embracesdk.internal.vitals.screenload.ScreenLoadTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
internal class FocalMomentTrackerTest {
    private val scheduler = FakeVitalsScheduler()
    private val emitted = mutableListOf<SmoothnessResult>()

    private var frameCounts = FrameCounts.ZERO

    private val tracker = FocalMomentTracker(
        scheduler = scheduler,
        reporter = SmoothnessReporter(emit = emitted::add),
        clock = { 0L },
        // Inert here: with no navigation signals the screen-load tracker stays idle and ignores frames.
        screenLoadTracker = ScreenLoadTracker(scheduler = scheduler, clock = { 0L }, emit = {}),
        onFrameCounted = { dropped, expectedFrames -> frameCounts += FrameCounts.frame(dropped, expectedFrames) },
    )

    private val responsivenessEmitted = mutableListOf<InteractionResponsivenessResult>()
    private val responsivenessReporter = InteractionResponsivenessReporter(
        emit = responsivenessEmitted::add,
        blockThresholdMs = 50L,
        probeIntervalMs = 16L,
    )

    private val mainLooper = shadowOf(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())

    // The probe's block callback, so a test can deliver a report at a chosen point in the moment.
    private lateinit var blockCallback: MainThreadProbe.BlockCallback

    // Captures interaction responsiveness too, probing the paused Robolectric main looper; its smoothness results share [emitted].
    private val responsiveTracker = FocalMomentTracker(
        scheduler = scheduler,
        reporter = SmoothnessReporter(emit = emitted::add),
        clock = { 0L },
        screenLoadTracker = ScreenLoadTracker(scheduler = scheduler, clock = { 0L }, emit = {}),
        responsivenessReporter = responsivenessReporter,
        probeFactory = { onBlock ->
            blockCallback = onBlock
            MainThreadProbe(
                looper = Looper.getMainLooper(),
                blockThresholdMs = 50L,
                onBlock = onBlock,
                errorHandler = FakeErrorHandler(),
            )
        },
    )

    // The tracker reads time from SystemClock.uptimeMillis; Robolectric keeps it paused until advance()
    // moves it, so all vsync times are expressed relative to [start], captured before the clock moves.
    private val start = SystemClock.uptimeMillis() * 1_000_000L

    @Test
    fun `after release the aftermath settles at the tighter threshold and emits on flush`() {
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs())

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
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 16.ms) // a frame so the interrupt emits
        advance(40)
        tracker.onScreenStart()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals("ends at the last frame, not the interrupt", 16L, result.durationMs)
        assertFalse("startup opens nothing", scheduler.scheduled)
    }

    @Test
    fun `a continuously redrawing interaction never caps`() {
        tracker.onInteractionStart(nowMs())

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
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 16.ms) // a frame so the interrupt emits
        advance(30)
        tracker.onScreenStop()

        val result = emitted.single()
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertEquals("ends at the last frame, not the interrupt", 16L, result.durationMs)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `a slow frame delivered after the settle is still counted, not dropped`() {
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs()) // focal moment opens here, at start (the touch-down)
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
        tracker.onInteractionStart(nowMs())
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
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 10.ms, jankNanos = 5.ms)
        tracker.onScreenStop()

        // only the in-focal-moment frame (~0.3) contributes; the pre-open 1s frame would have added ~60
        assertTrue("pre-open frame ignored", emitted.single().normalizedDroppedFrames < 1.0)
    }

    @Test
    fun `jank is forwarded per frame and normalized into the result`() {
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 1.ms, jankNanos = 1000.ms / 60) // one dropped 60fps frame -> 1.0
        tracker.onScreenStop()

        assertEquals(1.0, emitted.single().normalizedDroppedFrames, 0.01)
    }

    @Test
    fun `no frame trace is attached when the device is not sampled-in`() {
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 16.ms)
        tracker.onScreenStop()

        assertNull(emitted.single().frameTraceBase64)
    }

    @Test
    fun `a frame trace is attached when a FrameTraceRecorder is supplied`() {
        val sampledInEmitted = mutableListOf<SmoothnessResult>()
        val sampledInTracker = FocalMomentTracker(
            scheduler = scheduler,
            reporter = SmoothnessReporter(emit = sampledInEmitted::add),
            clock = { 0L },
            screenLoadTracker = ScreenLoadTracker(scheduler = scheduler, clock = { 0L }, emit = {}),
            frameTraceRecorder = FrameTraceRecorder(),
        )

        sampledInTracker.onInteractionStart(nowMs())
        sampledInTracker.onFrame(vsyncNanos = start + 16.ms, frameDispatchNanos = start + 16.ms, jankNanos = 0L, expectedFrames = 1)
        sampledInTracker.onScreenStop()

        assertNotNull(sampledInEmitted.single().frameTraceBase64)
    }

    @Test
    fun `every frame is counted, inside or outside a focal moment`() {
        redraw(vsyncNanos = start, jankNanos = 40.ms, expectedFrames = 4) // before any focal moment
        tracker.onInteractionStart(nowMs())
        redraw(vsyncNanos = start + 16.ms)
        redraw(vsyncNanos = start + 32.ms)

        assertEquals(3L to 6L, frameCounts.counts())
    }

    @Test
    fun `the opening touch-down's dispatch delay is reported and a later touch-down does not change it`() {
        val firstDownMs = nowMs()
        advance(30) // processed 30ms after it occurred: the focal moment starts here, at epoch 0
        responsiveTracker.onInteractionStart(firstDownMs)
        responsiveTracker.onInteractionEnd()

        advance(20)
        val secondDownMs = nowMs()
        advance(40) // a second tap inside the still-capturing moment, processed 40ms late
        responsiveTracker.onInteractionStart(secondDownMs)
        responsiveTracker.onInteractionEnd()

        settleAfter(100) // held at the second tap
        responsiveTracker.onScreenStop()

        val result = responsivenessEmitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals("the opening touch-down's delay, not the later one's", 30L, result.dispatchDelayMs)
        assertEquals("starts at the touch-down, 30ms before the moment start", -30L, result.startTimeMs)
        assertEquals("from the touch-down to the last activity", 90L, result.durationMs)
        assertEquals(50L, result.blockThresholdMs)
        assertEquals(16L, result.probeIntervalMs)
    }

    @Test
    fun `a touch-down stamped after its processing has no dispatch delay and starts at its processing`() {
        responsiveTracker.onInteractionStart(nowMs() + 50) // e.g. an injected event with a future event time
        responsiveTracker.onScreenStop()

        val result = responsivenessEmitted.single()
        assertEquals(0L, result.dispatchDelayMs)
        assertEquals("starts at the processing time: the moment start, epoch 0", 0L, result.startTimeMs)
        assertEquals("never negative", 0L, result.durationMs)
    }

    @Test
    fun `a moment opened by a move has no dispatch delay and spans the focal moment`() {
        // a touch-down's moment first, so its pending dispatch timing must not leak into the next one
        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onScreenStop()

        advance(10)
        responsiveTracker.onInteractionMove()
        // a frame whose render was dispatched before the move back-dates the focal moment start
        responsiveTracker.onFrame(vsyncNanos = start + 20.ms, frameDispatchNanos = start - 20.ms, jankNanos = 8.ms, expectedFrames = 1)
        responsiveTracker.onScreenStop()

        val smoothness = emitted.single()
        val result = responsivenessEmitted.last()
        assertEquals(2, responsivenessEmitted.size)
        assertEquals(FocalOutcome.INTERRUPTED, result.outcome)
        assertNull(result.dispatchDelayMs)
        assertEquals(-30L, smoothness.startTimeMs)
        assertEquals(smoothness.startTimeMs, result.startTimeMs)
        assertEquals(smoothness.durationMs, result.durationMs)
    }

    @Test
    fun `every moment emits its own blocks, including a moment with no blocks or frames`() {
        responsiveTracker.onInteractionStart(nowMs())
        responsivenessReporter.onBlock(60L)
        responsivenessReporter.onBlock(120L)
        responsiveTracker.onScreenStop()

        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onScreenStop()

        assertEquals(2, responsivenessEmitted.size)
        val (blocked, unblocked) = responsivenessEmitted
        assertEquals(120L, blocked.longestBlockMs)
        assertEquals(180L, blocked.totalBlockedMs)
        assertEquals(2, blocked.blockCount)
        assertEquals(0L, unblocked.longestBlockMs)
        assertEquals(0L, unblocked.totalBlockedMs)
        assertEquals(0, unblocked.blockCount)
        assertTrue("smoothness skips moments with no frames", emitted.isEmpty())
    }

    @Test
    fun `a main-thread block during a moment is counted in its result`() {
        responsiveTracker.onInteractionStart(nowMs())
        mainHandler.post { blockMainThread(200) } // a 200ms block queued ahead of the probe
        advance(250)
        responsiveTracker.onScreenStop()

        val result = responsivenessEmitted.single()
        assertEquals(1, result.blockCount)
        assertEquals("late by the block less the probe's pace", 184L, result.longestBlockMs)
        assertEquals(184L, result.totalBlockedMs)
        assertEquals("the end stops the probe", Duration.ZERO, mainLooper.nextScheduledTaskTime)
    }

    @Test
    fun `the probe stops while the moment is held and restarts when it resumes`() {
        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onInteractionEnd()
        responsiveTracker.onFrame(vsyncNanos = start + 16.ms, frameDispatchNanos = start + 16.ms, jankNanos = 0L, expectedFrames = 1)
        assertNotEquals("a capturing moment probes", Duration.ZERO, mainLooper.nextScheduledTaskTime)

        settleAfter(120)
        assertEquals("holding stops the probe", Duration.ZERO, mainLooper.nextScheduledTaskTime)

        // contiguous with the buffered settle: the moment resumes
        responsiveTracker.onFrame(vsyncNanos = start + 20.ms, frameDispatchNanos = start + 20.ms, jankNanos = 0L, expectedFrames = 1)
        assertEquals("resuming restarts the probe from now", Duration.ofMillis(nowMs()), mainLooper.nextScheduledTaskTime)
    }

    @Test
    fun `a block in progress at the settle check defers the settle until after the block`() {
        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onInteractionEnd()
        responsiveTracker.onFrame(vsyncNanos = start + 16.ms, frameDispatchNanos = start + 16.ms, jankNanos = 0L, expectedFrames = 1)

        // the settle check comes due 150ms into a 400ms main-thread block
        blockMainThread(150)
        scheduler.runPending()
        assertNotEquals("not held: the probe still waits to run", Duration.ZERO, mainLooper.nextScheduledTaskTime)
        assertFalse("the settle waits for the block report", scheduler.scheduled)

        blockMainThread(250)
        mainLooper.idle() // the block ends; the probe runs and reports it

        settleAfter(99)
        assertTrue("99ms after the block: not settled", scheduler.scheduled)
        settleAfter(1)
        assertFalse("100ms after the block: settled", scheduler.scheduled)

        responsiveTracker.onScreenStop()
        val result = responsivenessEmitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(1, result.blockCount)
        assertEquals("the whole block: the first probe is due at the moment start", 400L, result.longestBlockMs)
        assertEquals("ends at the block's end", 400L, result.durationMs)
        assertEquals("smoothness covers the block too", 400L, emitted.single().durationMs)
    }

    @Test
    fun `a block reported after the moment is held resumes it until after the block`() {
        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onInteractionEnd()
        responsiveTracker.onFrame(vsyncNanos = start + 16.ms, frameDispatchNanos = start + 16.ms, jankNanos = 0L, expectedFrames = 1)
        settleAfter(120) // held at the frame
        assertEquals(Duration.ZERO, mainLooper.nextScheduledTaskTime)

        // the probe posted this report just before the settle check held the moment
        blockCallback.onBlock(nowMs(), 80L)
        assertNotEquals("resuming restarts the probe", Duration.ZERO, mainLooper.nextScheduledTaskTime)

        settleAfter(99)
        assertTrue("99ms after the block: not settled", scheduler.scheduled)
        settleAfter(1)
        responsiveTracker.onScreenStop() // flushes the held moment

        val result = responsivenessEmitted.single()
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(1, result.blockCount)
        assertEquals(80L, result.longestBlockMs)
        assertEquals("ends at the block's end, not the buffered settle", 120L, result.durationMs)
        assertEquals(120L, emitted.single().durationMs)
    }

    @Test
    fun `a block reported after the moment completed is ignored`() {
        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onScreenStop()

        blockCallback.onBlock(nowMs(), 80L)
        assertFalse("a completed moment schedules nothing", scheduler.scheduled)

        responsiveTracker.onInteractionStart(nowMs())
        responsiveTracker.onScreenStop()
        assertEquals(listOf(0, 0), responsivenessEmitted.map { it.blockCount })
    }

    @Test
    fun `close stops the probe of an open moment`() {
        responsiveTracker.onInteractionStart(nowMs())
        assertNotEquals(Duration.ZERO, mainLooper.nextScheduledTaskTime)

        responsiveTracker.close()
        assertEquals(Duration.ZERO, mainLooper.nextScheduledTaskTime)
    }

    private fun nowMs() = SystemClock.uptimeMillis()

    /** Advances the clock by [millis] with a responsive main thread, so a running probe stays on time. */
    private fun advance(millis: Long) = mainLooper.idleFor(Duration.ofMillis(millis))

    /** Advances the clock by [millis] without running the main looper, as a blocked main thread would. */
    private fun blockMainThread(millis: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(millis))

    /** Advances the clock by [millis] and then runs the pending settle check. */
    private fun settleAfter(millis: Long) {
        advance(millis)
        scheduler.runPending()
    }

    /**
     * A frame delivered on the Vitals thread; processed synchronously. [frameDispatchNanos] defaults to
     * the vsync (an instantaneous frame); pass an earlier value to model a frame whose render took time.
     */
    private fun redraw(
        vsyncNanos: Long,
        jankNanos: Long = 0L,
        frameDispatchNanos: Long = vsyncNanos,
        expectedFrames: Int = 1,
    ) {
        tracker.onFrame(vsyncNanos, frameDispatchNanos, jankNanos, expectedFrames)
    }

    /** Milliseconds expressed in the nanosecond base the tracker works in. */
    private val Int.ms: Long get() = this * 1_000_000L
}
