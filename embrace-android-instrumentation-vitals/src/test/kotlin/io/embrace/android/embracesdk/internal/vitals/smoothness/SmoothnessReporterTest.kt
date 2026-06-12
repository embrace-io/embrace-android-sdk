package io.embrace.android.embracesdk.internal.vitals.smoothness

import org.junit.Assert.assertEquals
import org.junit.Test

internal class SmoothnessReporterTest {

    private val emitted = mutableListOf<SmoothnessResult>()
    private val reporter = SmoothnessReporter(emit = emitted::add)

    @Test
    fun `normalizes jank to 60fps reference frames per the SMOOTHNESS doc examples`() {
        reporter.onFocalMomentStart(startTimeMs = 1_000)
        // one dropped 120Hz frame (8.33ms) -> 0.5
        reporter.onFocalMomentFrame(jankNanos = 1_000_000_000L / 120)
        // one dropped 30Hz frame (33.33ms) -> 2.0
        reporter.onFocalMomentFrame(jankNanos = 1_000_000_000L / 30)
        // a one-second frame on a 30Hz display -> ~60
        reporter.onFocalMomentFrame(jankNanos = 1_000_000_000L)
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, durationMs = 1_100)

        val result = emitted.single()
        // 0.5 + 2.0 + 60.0
        assertEquals(62.5, result.normalizedDroppedFrames, 0.01)
        assertEquals(FocalOutcome.SETTLED, result.outcome)
        assertEquals(1_000L, result.startTimeMs)
        assertEquals(1_100L, result.durationMs)
        assertEquals(3, result.frameCount)
    }

    @Test
    fun `on-time frames contribute zero dropped frames`() {
        reporter.onFocalMomentStart(0)
        reporter.onFocalMomentFrame(0)
        reporter.onFocalMomentFrame(0)
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, 10)

        val result = emitted.single()
        assertEquals(0.0, result.normalizedDroppedFrames, 0.0)
    }

    @Test
    fun `a focal moment with no frames is not a smoothness signal and emits nothing`() {
        reporter.onFocalMomentStart(startTimeMs = 0)
        // e.g. a tap with no visual change: the focal moment opened and closed without a redraw
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, durationMs = 100)

        assertEquals(0, emitted.size)
    }

    @Test
    fun `a single frame is enough to emit`() {
        reporter.onFocalMomentStart(startTimeMs = 0)
        reporter.onFocalMomentFrame(jankNanos = 0)
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, durationMs = 100)

        assertEquals(1, emitted.size)
    }

    @Test
    fun `resets accumulators between focal moments`() {
        reporter.onFocalMomentStart(0)
        reporter.onFocalMomentFrame(1_000_000_000L)
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, 1)

        reporter.onFocalMomentStart(0)
        reporter.onFocalMomentFrame(0)
        reporter.onFocalMomentEnd(FocalOutcome.SETTLED, 1)

        val second = emitted.last()
        assertEquals(0.0, second.normalizedDroppedFrames, 0.0)
    }
}
