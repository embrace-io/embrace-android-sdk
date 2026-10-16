package io.embrace.android.embracesdk.internal.vitals

import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.S
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FrameMetricsStrategyTest {

    @Test
    fun `below API 26 the vsync time is approximated by the metrics-delivery time`() {
        // Legacy reports the delivery time (SystemClock.uptimeMillis, in nanos). Robolectric keeps the
        // clock paused, so it's stable across both reads.
        val expected = SystemClock.uptimeMillis() * 1_000_000L
        assertEquals(expected, strategy(sdkInt = N).vsyncNanos(frameMetrics()))
    }

    @Test
    fun `at API 26 and above the vsync time is read from the frame, not the delivery time`() {
        forEachSdk(O, S) { sdkInt ->
            // The frame carries its own VSYNC_TIMESTAMP, distinct from the delivery time.
            assertEquals(123L, strategy(sdkInt = sdkInt).vsyncNanos(frameMetrics(vsyncNanos = 123L)))
        }
    }

    @Test
    fun `a frame that meets its budget reports zero jank on every platform`() {
        forEachSdk(N, O, S) { sdkInt ->
            assertEquals(0L, strategy(sdkInt = sdkInt).jankNanos(frameMetrics()))
        }
    }

    @Test
    fun `an over-budget frame reports positive jank when it is not the first draw`() {
        forEachSdk(O, S) { sdkInt ->
            // A long frame that isn't first-draw reports positive jank. On API 31+ the budget comes from
            // DEADLINE; below that, from the refresh interval.
            val overran =
                frameMetrics(totalDurationNanos = 100_000_000L, firstDraw = false, deadlineNanos = 16_666_666L)
            assertTrue(strategy(sdkInt = sdkInt).jankNanos(overran) > 0L)
        }
    }

    @Test
    fun `below API 31 the jank budget tracks a live refresh-rate change`() {
        forEachSdk(N, O) { sdkInt ->
            // A 25ms frame: within the 2x buffer at 60Hz (budget 33.3ms), past it at 120Hz (budget 16.7ms).
            val twentyFiveMs = frameMetrics(totalDurationNanos = 25_000_000L, firstDraw = false)
            val strategy = strategy(intervalNanos = 16_666_666L, sdkInt = sdkInt) // 60Hz
            assertEquals(0L, strategy.jankNanos(twentyFiveMs))
            strategy.onRefreshIntervalChanged(8_333_333L) // 120Hz
            assertTrue(strategy.jankNanos(twentyFiveMs) > 0L)
        }
    }

    @Test
    fun `a frame within the buffered budget is not counted as jank`() {
        val strategy = strategy(intervalNanos = 16_666_666L, sdkInt = O) // 60Hz, budget 33.3ms with the 2x buffer
        // 20ms: past one interval but within the buffer -> not jank
        assertEquals(0L, strategy.jankNanos(frameMetrics(totalDurationNanos = 20_000_000L, firstDraw = false)))
        // 40ms: past the buffer -> jank
        assertTrue(strategy.jankNanos(frameMetrics(totalDurationNanos = 40_000_000L, firstDraw = false)) > 0L)
    }

    @Test
    fun `the jank heuristic multiplier is configurable`() {
        val twentyMs = frameMetrics(totalDurationNanos = 20_000_000L, firstDraw = false)
        assertEquals("buffered at 2x", 0L, strategy(intervalNanos = 16_666_666L, multiplier = 2.0, sdkInt = O).jankNanos(twentyMs))
        assertTrue("unbuffered at 1x", strategy(intervalNanos = 16_666_666L, multiplier = 1.0, sdkInt = O).jankNanos(twentyMs) > 0L)
    }

    @Test
    fun `at API 26 and above the dispatch time is the vsync less the full render duration`() {
        forEachSdk(O, S) { sdkInt ->
            // A stuck frame: visible at vsync 600ms after a 500ms render -> dispatched at 100ms.
            val stuck = frameMetrics(vsyncNanos = 600_000_000L, totalDurationNanos = 500_000_000L)
            assertEquals(100_000_000L, strategy(sdkInt = sdkInt).frameDispatchNanos(stuck))
        }
    }

    @Test
    fun `below API 26 the dispatch time is the metrics-delivery time less the render duration`() {
        // No real vsync exists, so it is derived from the delivery time (the paused Robolectric clock).
        val expected = SystemClock.uptimeMillis() * 1_000_000L - 500_000_000L
        assertEquals(expected, strategy(sdkInt = N).frameDispatchNanos(frameMetrics(totalDurationNanos = 500_000_000L)))
    }

    @Test
    fun `from API 26 a first-draw frame reports zero jank — it is the baseline, not a dropped frame`() {
        forEachSdk(O, S) { sdkInt ->
            // A long frame that would otherwise be janky, but flagged as first-draw, reports zero jank.
            val firstDraw =
                frameMetrics(totalDurationNanos = 100_000_000L, firstDraw = true, deadlineNanos = 16_666_666L)
            assertEquals(0L, strategy(sdkInt = sdkInt).jankNanos(firstDraw))
        }
    }

    @Test
    fun `a frame that is not dropped is one expected frame, however long it took`() {
        forEachSdk(N, O, S) { sdkInt ->
            // 20ms at a 16ms interval: over one interval but within the 2x budget
            val slowButNotDropped = frameMetrics(totalDurationNanos = 20_000_000L, deadlineNanos = 16_000_000L)
            assertEquals(1, strategy(sdkInt = sdkInt).expectedFrames(slowButNotDropped, jankNanos = 0L))
        }
    }

    @Test
    fun `a dropped frame is the vsyncs it occupied, rounded up`() {
        forEachSdk(N, O, S) { sdkInt ->
            // API 31+ divides by DEADLINE, below by the refresh interval; both are 16ms here
            val strategy = strategy(sdkInt = sdkInt)
            val fortyMs = frameMetrics(totalDurationNanos = 40_000_000L, deadlineNanos = 16_000_000L)
            assertEquals(3, strategy.expectedFrames(fortyMs, strategy.jankNanos(fortyMs)))

            val exactlyThree = frameMetrics(totalDurationNanos = 48_000_000L, deadlineNanos = 16_000_000L)
            assertEquals(3, strategy.expectedFrames(exactlyThree, strategy.jankNanos(exactlyThree)))

            val oneSecond = frameMetrics(totalDurationNanos = 1_000_000_000L, deadlineNanos = 16_000_000L)
            assertEquals(63, strategy.expectedFrames(oneSecond, strategy.jankNanos(oneSecond)))
        }
    }

    @Test
    fun `below API 31 expected frames track a live refresh-rate change`() {
        forEachSdk(N, O) { sdkInt ->
            val strategy = strategy(intervalNanos = 16_000_000L, sdkInt = sdkInt)
            val fortyMs = frameMetrics(totalDurationNanos = 40_000_000L)
            strategy.onRefreshIntervalChanged(8_000_000L) // 125Hz
            assertEquals(5, strategy.expectedFrames(fortyMs, strategy.jankNanos(fortyMs)))
        }
    }

    @Test
    fun `a first-draw frame is one expected frame`() {
        forEachSdk(O, S) { sdkInt ->
            val strategy = strategy(sdkInt = sdkInt)
            val firstDraw =
                frameMetrics(totalDurationNanos = 100_000_000L, firstDraw = true, deadlineNanos = 16_000_000L)
            assertEquals(1, strategy.expectedFrames(firstDraw, strategy.jankNanos(firstDraw)))
        }
    }

    @Test
    fun `a high refresh rate deadline is not clamped`() {
        // 240Hz: a 1s frame spans 240 vsyncs
        val strategy = strategy(sdkInt = S)
        val oneSecond = frameMetrics(totalDurationNanos = 1_000_000_000L, deadlineNanos = 4_166_667L)
        assertEquals(240, strategy.expectedFrames(oneSecond, strategy.jankNanos(oneSecond)))
    }

    private fun strategy(intervalNanos: Long = 16_000_000L, multiplier: Double = 2.0, sdkInt: Int) =
        FrameMetricsStrategy.create(intervalNanos, multiplier, VersionChecker { min -> sdkInt >= min })

    private fun forEachSdk(vararg sdkInts: Int, action: (sdkInt: Int) -> Unit) {
        sdkInts.forEach { sdkInt ->
            try {
                action(sdkInt)
            } catch (e: AssertionError) {
                throw AssertionError("Failed at API $sdkInt", e)
            }
        }
    }

    /**
     * A mocked [FrameMetrics] that returns the given values for the metrics the strategies read. The
     * `*_FRAME`/`DEADLINE` constants are compile-time ints, so this works even on SDKs where those
     * metrics don't exist — the strategy under test simply won't query them there.
     */
    private fun frameMetrics(
        totalDurationNanos: Long = 0L,
        firstDraw: Boolean = false,
        vsyncNanos: Long = 0L,
        deadlineNanos: Long = 0L,
    ): FrameMetrics = mockk {
        every { getMetric(FrameMetrics.TOTAL_DURATION) } returns totalDurationNanos
        every { getMetric(FrameMetrics.FIRST_DRAW_FRAME) } returns if (firstDraw) 1L else 0L
        every { getMetric(FrameMetrics.VSYNC_TIMESTAMP) } returns vsyncNanos
        every { getMetric(FrameMetrics.DEADLINE) } returns deadlineNanos
    }
}
