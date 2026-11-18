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
    fun `the jank budget is the refresh interval scaled by the jank heuristic multiplier`() {
        forEachSdk(N, O, S) { sdkInt ->
            val buffered = strategy(intervalNanos = SIXTY_HZ, multiplier = 2.0, sdkInt = sdkInt)
            val unbuffered = strategy(intervalNanos = SIXTY_HZ, multiplier = 1.0, sdkInt = sdkInt)
            assertEquals("buffered at 2x", 2 * SIXTY_HZ, buffered.jankBudgetNanos(frameMetrics()))
            assertEquals("unbuffered at 1x", SIXTY_HZ, unbuffered.jankBudgetNanos(frameMetrics()))
        }
    }

    @Test
    fun `the jank budget tracks a live refresh-rate change`() {
        forEachSdk(N, O, S) { sdkInt ->
            val strategy = strategy(intervalNanos = SIXTY_HZ, sdkInt = sdkInt) // 60Hz
            assertEquals(2 * SIXTY_HZ, strategy.jankBudgetNanos(frameMetrics()))
            strategy.onRefreshIntervalChanged(8_333_333L) // 120Hz
            assertEquals(2 * 8_333_333L, strategy.jankBudgetNanos(frameMetrics()))
        }
    }

    @Test
    fun `from API 26 a first-draw frame can't jank — it is the baseline, not a dropped frame`() {
        forEachSdk(O, S) { sdkInt ->
            assertEquals(Long.MAX_VALUE, strategy(sdkInt = sdkInt).jankBudgetNanos(frameMetrics(firstDraw = true)))
        }
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
     * `*_FRAME` constants are compile-time ints, so this works even on SDKs where those metrics don't
     * exist — the strategy under test simply won't query them there.
     */
    private fun frameMetrics(
        totalDurationNanos: Long = 0L,
        firstDraw: Boolean = false,
        vsyncNanos: Long = 0L,
    ): FrameMetrics = mockk {
        every { getMetric(FrameMetrics.TOTAL_DURATION) } returns totalDurationNanos
        every { getMetric(FrameMetrics.FIRST_DRAW_FRAME) } returns if (firstDraw) 1L else 0L
        every { getMetric(FrameMetrics.VSYNC_TIMESTAMP) } returns vsyncNanos
    }

    private companion object {
        const val SIXTY_HZ = 16_666_666L
    }
}
