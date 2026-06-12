package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
internal class FrameMetricsStrategyTest {

    private fun setSdkInt(level: Int) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", level)
    }

    private fun strategy(intervalNanos: Long = 16_000_000L, multiplier: Double = 2.0) =
        FrameMetricsStrategy.create(intervalNanos, multiplier)

    private fun emptyFrameMetrics(): FrameMetrics =
        FrameMetrics::class.java.getDeclaredConstructor().newInstance()

    /** Builds a [FrameMetrics] with a given total render time, optionally flagged as the first draw. */
    private fun frameMetrics(totalDurationNanos: Long, firstDraw: Boolean): FrameMetrics {
        val fm = emptyFrameMetrics()
        val timingData = FrameMetrics::class.java.getDeclaredField("mTimingData")
            .apply { isAccessible = true }
            .get(fm) as LongArray

        val indexClass = Class.forName("android.view.FrameMetrics\$Index")
        fun index(name: String): Int =
            indexClass.getDeclaredField(name).apply { isAccessible = true }.getInt(null)

        // TOTAL_DURATION is INTENDED_VSYNC -> FRAME_COMPLETED; leave the start at 0.
        timingData[index("FRAME_COMPLETED")] = totalDurationNanos
        if (firstDraw) {
            // FIRST_DRAW_FRAME is bit 0 of the FLAGS slot.
            timingData[index("FLAGS")] = timingData[index("FLAGS")] or 1L
        }
        return fm
    }

    @Test
    fun `below API 26 the vsync time is approximated by the metrics-delivery time`() {
        setSdkInt(Build.VERSION_CODES.N)
        // Legacy reports the delivery time (SystemClock.uptimeMillis, in nanos). Robolectric keeps the
        // clock paused, so it's stable across both reads.
        val expected = SystemClock.uptimeMillis() * 1_000_000L
        assertEquals(expected, strategy().vsyncNanos(emptyFrameMetrics()))
    }

    @Test
    fun `at API 26 and above the vsync time is read from the frame, not the delivery time`() {
        // VSYNC_TIMESTAMP of an empty frame is 0, distinct from the delivery time.
        setSdkInt(Build.VERSION_CODES.O)
        assertEquals(0L, strategy().vsyncNanos(emptyFrameMetrics()))

        setSdkInt(Build.VERSION_CODES.S)
        assertEquals(0L, strategy().vsyncNanos(emptyFrameMetrics()))
    }

    @Test
    fun `a frame that meets its budget reports zero jank on every platform`() {
        intArrayOf(Build.VERSION_CODES.N, Build.VERSION_CODES.O, Build.VERSION_CODES.S).forEach { sdk ->
            setSdkInt(sdk)
            assertEquals(0L, strategy().jankNanos(emptyFrameMetrics()))
        }
    }

    @Test
    fun `an over-budget frame reports positive jank when it is not the first draw`() {
        // The same long frame, when not first-draw, reports positive jank.
        val overran = frameMetrics(totalDurationNanos = 100_000_000L, firstDraw = false)
        intArrayOf(Build.VERSION_CODES.O, Build.VERSION_CODES.S).forEach { sdk ->
            setSdkInt(sdk)
            assertTrue(strategy().jankNanos(overran) > 0L)
        }
    }

    @Test
    fun `below API 31 the jank budget tracks a live refresh-rate change`() {
        // A 25ms frame: within the 2x buffer at 60Hz (budget 33.3ms), past it at 120Hz (budget 16.7ms).
        val twentyFiveMs = frameMetrics(totalDurationNanos = 25_000_000L, firstDraw = false)
        intArrayOf(Build.VERSION_CODES.N, Build.VERSION_CODES.O).forEach { sdk ->
            setSdkInt(sdk)
            val strategy = strategy(intervalNanos = 16_666_666L) // 60Hz
            assertEquals(0L, strategy.jankNanos(twentyFiveMs))
            strategy.onRefreshIntervalChanged(8_333_333L) // 120Hz
            assertTrue(strategy.jankNanos(twentyFiveMs) > 0L)
        }
    }

    @Test
    fun `a frame within the buffered budget is not counted as jank`() {
        setSdkInt(Build.VERSION_CODES.O)
        val strategy = strategy(intervalNanos = 16_666_666L) // 60Hz, budget 33.3ms with the 2x buffer
        // 20ms: past one interval but within the buffer -> not jank
        assertEquals(0L, strategy.jankNanos(frameMetrics(totalDurationNanos = 20_000_000L, firstDraw = false)))
        // 40ms: past the buffer -> jank
        assertTrue(strategy.jankNanos(frameMetrics(totalDurationNanos = 40_000_000L, firstDraw = false)) > 0L)
    }

    @Test
    fun `the jank heuristic multiplier is configurable`() {
        setSdkInt(Build.VERSION_CODES.O)
        val twentyMs = frameMetrics(totalDurationNanos = 20_000_000L, firstDraw = false)
        assertEquals("buffered at 2x", 0L, strategy(intervalNanos = 16_666_666L, multiplier = 2.0).jankNanos(twentyMs))
        assertTrue("unbuffered at 1x", strategy(intervalNanos = 16_666_666L, multiplier = 1.0).jankNanos(twentyMs) > 0L)
    }

    @Test
    fun `from API 26 a first-draw frame reports zero jank — it is the baseline, not a dropped frame`() {
        val firstDraw = frameMetrics(totalDurationNanos = 100_000_000L, firstDraw = true)
        // The helper produced a first-draw frame, read via the public API.
        assertEquals(1L, firstDraw.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
        intArrayOf(Build.VERSION_CODES.O, Build.VERSION_CODES.S).forEach { sdk ->
            setSdkInt(sdk)
            assertEquals(0L, strategy().jankNanos(firstDraw))
        }
    }
}
