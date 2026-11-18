package io.embrace.android.embracesdk.internal.vitals

import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.S
import android.view.FrameMetrics
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class VitalsFrameMetricsListenerTest {

    private val throwingStrategy = object : FrameMetricsStrategy {
        override fun vsyncNanos(frameMetrics: FrameMetrics): Long = error("boom")
        override fun frameStartNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun jankBudgetNanos(frameMetrics: FrameMetrics): Long = Long.MAX_VALUE
        override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = 0L
    }

    @Test
    fun `frame callbacks that throw repeatedly across windows track one error`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val errors = OnceUiCallbackErrorTracker(errorHandler)
        val listeners =
            List(2) { VitalsFrameMetricsListener(FakeFocalInteractionCallbacks(), throwingStrategy, errors) }
        repeat(5) {
            listeners.forEach { it.onFrameMetricsAvailable(mockk<Window>(), mockk<FrameMetrics>(), 0) }
        }
        assertEquals(listOf(SdkErrorType.UiCallbackFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a frame that meets its budget reports zero jank on every platform`() {
        forEachSdk(N, O, S) { sdkInt ->
            assertEquals(0L, strategy(sdkInt = sdkInt).frameAlone(totalNanos = 0L).first)
        }
    }

    @Test
    fun `an over-budget frame reports positive jank when it is not the first draw`() {
        forEachSdk(O, S) { sdkInt ->
            assertTrue(strategy(sdkInt = sdkInt).frameAlone(totalNanos = 100_000_000L).first > 0L)
        }
    }

    @Test
    fun `the jank budget and expected frames ignore DEADLINE`() {
        // A frame delayed 83ms by the main thread: the platform moves its DEADLINE out to 116.7ms, but at 60Hz its budget is
        // two refresh intervals, and it spans 7 of them.
        val late = strategy(sdkInt = S).frameAlone(totalNanos = 100_200_000L, deadlineNanos = 116_666_666L)
        assertEquals((100_200_000L - 33_333_332L) to 7, late)
    }

    @Test
    fun `a near-zero or negative DEADLINE changes neither the jank nor the expected frames`() {
        val strategy = strategy(sdkInt = S)
        val sane = strategy.frameAlone(totalNanos = 50_000_000L, deadlineNanos = SIXTY_HZ)
        assertEquals(4, sane.second)
        listOf(10_000L, -33_333_333L).forEach { deadlineNanos ->
            assertEquals(sane, strategy.frameAlone(totalNanos = 50_000_000L, deadlineNanos = deadlineNanos))
        }
    }

    @Test
    fun `a frame of 120 refresh intervals drops 119 frames`() {
        val expected = strategy(sdkInt = S).frameAlone(totalNanos = 120 * SIXTY_HZ, deadlineNanos = SIXTY_HZ).second

        assertEquals(120, expected)
        assertEquals(119L, FrameCounts.frame(dropped = true, expectedFrames = expected).dropped)
    }

    @Test
    fun `a frame within the buffered budget is not counted as jank`() {
        val strategy = strategy(sdkInt = O) // 60Hz, budget 33.3ms with the 2x buffer
        // 20ms: past one interval but within the buffer -> not jank
        assertEquals(0L, strategy.frameAlone(totalNanos = 20_000_000L).first)
        // 40ms: past the buffer -> jank
        assertTrue(strategy.frameAlone(totalNanos = 40_000_000L).first > 0L)
    }

    @Test
    fun `from API 26 a first-draw frame reports zero jank and one expected frame`() {
        forEachSdk(O, S) { sdkInt ->
            // A long frame that would otherwise be janky, but flagged as first-draw, is the baseline, not a dropped frame.
            assertEquals(0L to 1, strategy(sdkInt = sdkInt).frameAlone(totalNanos = 100_000_000L, firstDraw = true))
        }
    }

    @Test
    fun `a frame that is not dropped is one expected frame, however long it took`() {
        forEachSdk(N, O, S) { sdkInt ->
            // 20ms at a 16ms interval: over one interval but within the 2x budget
            assertEquals(0L to 1, strategy(intervalNanos = 16_000_000L, sdkInt = sdkInt).frameAlone(totalNanos = 20_000_000L))
        }
    }

    @Test
    fun `a dropped frame is the vsyncs it occupied, rounded up`() {
        forEachSdk(N, O, S) { sdkInt ->
            val strategy = strategy(intervalNanos = 16_000_000L, sdkInt = sdkInt)
            assertEquals(3, strategy.frameAlone(totalNanos = 40_000_000L).second)
            assertEquals(3, strategy.frameAlone(totalNanos = 48_000_000L).second)
            assertEquals(63, strategy.frameAlone(totalNanos = 1_000_000_000L).second)
        }
    }

    @Test
    fun `expected frames track a live refresh-rate change`() {
        forEachSdk(N, O, S) { sdkInt ->
            val strategy = strategy(intervalNanos = 16_000_000L, sdkInt = sdkInt)
            strategy.onRefreshIntervalChanged(8_000_000L) // 125Hz
            assertEquals(5, strategy.frameAlone(totalNanos = 40_000_000L).second)
        }
    }

    @Test
    fun `a high refresh rate is not clamped`() {
        // 240Hz: a 1s frame spans 240 vsyncs
        assertEquals(240, strategy(intervalNanos = 4_166_667L, sdkInt = S).frameAlone(totalNanos = 1_000_000_000L).second)
    }

    @Test
    fun `frames that present on time while a frame is stuck don't shorten its stall`() {
        val callbacks = FakeFocalInteractionCallbacks()
        val listener = listener(callbacks)

        // A RenderThread animation keeps presenting every vsync while the main thread blocks the frame intended at 0
        (1..5).forEach { vsync -> listener.frame(startNanos = vsync * SIXTY_HZ, totalNanos = SIXTY_HZ) }
        listener.frame(startNanos = 0L, totalNanos = 6 * SIXTY_HZ)

        assertEquals(List(5) { 0L to 1 } + ((6 * SIXTY_HZ - 2 * SIXTY_HZ) to 6), callbacks.frames)
    }

    @Test
    fun `pipelined stuck frames count each vsync once`() {
        val callbacks = FakeFocalInteractionCallbacks()
        val listener = listener(callbacks)

        listener.frame(startNanos = 0L, totalNanos = 6 * SIXTY_HZ)
        listener.frame(startNanos = SIXTY_HZ, totalNanos = 6 * SIXTY_HZ)
        listener.frame(startNanos = 2 * SIXTY_HZ, totalNanos = 6 * SIXTY_HZ)

        // 8 vsyncs of wall time, from 0 to 8 intervals, for 3 frames: 5 missed. Counted alone, each frame would be 6.
        assertEquals(listOf(4 * SIXTY_HZ to 6, SIXTY_HZ to 1, SIXTY_HZ to 1), callbacks.frames)
    }

    @Test
    fun `a stuck frame that ends within an earlier stall adds no jank and one vsync`() {
        val callbacks = FakeFocalInteractionCallbacks()
        val listener = listener(callbacks)

        listener.frame(startNanos = 0L, totalNanos = 6 * SIXTY_HZ)
        listener.frame(startNanos = 2 * SIXTY_HZ, totalNanos = 3 * SIXTY_HZ)

        assertEquals(0L to 1, callbacks.frames.last())
    }

    @Test
    fun `an earlier stall that ended before the frame's budget changes nothing`() {
        forEachSdk(O, S) { sdkInt ->
            val strategy = strategy(sdkInt = sdkInt)
            val callbacks = FakeFocalInteractionCallbacks()
            val listener = listener(callbacks, strategy)

            listener.frame(startNanos = 3 * SIXTY_HZ, totalNanos = 6 * SIXTY_HZ) // stalls through 9 intervals
            listener.frame(startNanos = 10 * SIXTY_HZ, totalNanos = 5 * SIXTY_HZ)

            assertEquals(strategy.frameAlone(startNanos = 10 * SIXTY_HZ, totalNanos = 5 * SIXTY_HZ), callbacks.frames.last())
        }
    }

    @Test
    fun `each window tracks its own stalls`() {
        val first = FakeFocalInteractionCallbacks()
        val second = FakeFocalInteractionCallbacks()

        listener(first).frame(startNanos = 0L, totalNanos = 6 * SIXTY_HZ)
        listener(second).frame(startNanos = SIXTY_HZ, totalNanos = 6 * SIXTY_HZ)

        assertEquals(6, first.frames.single().second)
        assertEquals(6, second.frames.single().second)
    }

    private fun strategy(intervalNanos: Long = SIXTY_HZ, multiplier: Double = 2.0, sdkInt: Int = S) =
        FrameMetricsStrategy.create(intervalNanos, multiplier, VersionChecker { min -> sdkInt >= min })

    private fun listener(callbacks: FakeFocalInteractionCallbacks, frameMetricsStrategy: FrameMetricsStrategy = strategy()) =
        VitalsFrameMetricsListener(callbacks, frameMetricsStrategy, OnceUiCallbackErrorTracker(FakeErrorHandler()))

    /** The frame's `(jankNanos, expectedFrames)` with no earlier stall to overlap. */
    private fun FrameMetricsStrategy.frameAlone(
        startNanos: Long = 0L,
        totalNanos: Long,
        firstDraw: Boolean = false,
        deadlineNanos: Long = 0L,
    ): Pair<Long, Int> {
        val callbacks = FakeFocalInteractionCallbacks()
        listener(callbacks, this).frame(startNanos, totalNanos, firstDraw, deadlineNanos)
        return callbacks.frames.single()
    }

    /**
     * Delivers a mocked [FrameMetrics] to the listener. The `*_FRAME`/`DEADLINE` constants are compile-time ints, so this works
     * even on SDKs where those metrics don't exist — the strategy under test simply won't query them there.
     */
    private fun VitalsFrameMetricsListener.frame(
        startNanos: Long,
        totalNanos: Long,
        firstDraw: Boolean = false,
        deadlineNanos: Long = 0L,
    ) {
        val frameMetrics = mockk<FrameMetrics> {
            every { getMetric(FrameMetrics.TOTAL_DURATION) } returns totalNanos
            every { getMetric(FrameMetrics.FIRST_DRAW_FRAME) } returns if (firstDraw) 1L else 0L
            every { getMetric(FrameMetrics.VSYNC_TIMESTAMP) } returns startNanos
            every { getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) } returns startNanos
            every { getMetric(FrameMetrics.DEADLINE) } returns deadlineNanos
        }
        onFrameMetricsAvailable(mockk<Window>(), frameMetrics, 0)
    }

    private fun forEachSdk(vararg sdkInts: Int, action: (sdkInt: Int) -> Unit) {
        sdkInts.forEach { sdkInt ->
            try {
                action(sdkInt)
            } catch (e: AssertionError) {
                throw AssertionError("Failed at API $sdkInt", e)
            }
        }
    }

    private companion object {
        const val SIXTY_HZ = 16_666_666L
    }
}
