package io.embrace.android.embracesdk.internal.vitals

import org.junit.Assert.assertEquals
import org.junit.Test

internal class FrameCountsTest {

    @Test
    fun `zero has no frames`() {
        assertEquals(0 to 0, FrameCounts.ZERO.counts())
    }

    @Test
    fun `a rendered frame is one expected frame and none dropped`() {
        assertEquals(0 to 1, FrameCounts.frame(dropped = false, expectedFrames = 1).counts())
    }

    @Test
    fun `a dropped frame drops every vsync it spanned except the one it appeared on`() {
        // 60Hz, 0.5s over its deadline: 31 vsyncs, 30 of which showed nothing new
        assertEquals(30 to 31, FrameCounts.frame(dropped = true, expectedFrames = 31).counts())
    }

    @Test
    fun `dropped and expected frames accumulate independently`() {
        val counts = FrameCounts.ZERO +
            FrameCounts.frame(dropped = false, expectedFrames = 1) +
            FrameCounts.frame(dropped = true, expectedFrames = 3) +
            FrameCounts.frame(dropped = true, expectedFrames = 61)

        assertEquals(62 to 65, counts.counts())
    }

    @Test
    fun `overlapping intervals each get their own counts`() {
        val outerStart = FrameCounts.ZERO
        val innerStart = outerStart + FrameCounts.frame(dropped = true, expectedFrames = 2)
        val end = innerStart + FrameCounts.frame(dropped = false, expectedFrames = 1)

        assertEquals(0 to 1, (end - innerStart).counts())
        assertEquals(1 to 3, (end - outerStart).counts())
    }

    @Test
    fun `a frame spanning more vsyncs than the maximum is clamped to it`() {
        // e.g. a nonsense 1ns deadline makes a 3s frame 3e9 vsyncs
        assertEquals(3599 to 3600, FrameCounts.frame(dropped = true, expectedFrames = 3_000_000_000L).counts())
    }

    @Test
    fun `a frame spanning fewer than one vsync is clamped to one`() {
        assertEquals(0 to 1, FrameCounts.frame(dropped = true, expectedFrames = 0L).counts())
        assertEquals(0 to 1, FrameCounts.frame(dropped = true, expectedFrames = -5L).counts())
    }

    @Test
    fun `a large expected count does not carry into the dropped count`() {
        var counts = FrameCounts.ZERO
        repeat(500_000) { counts += FrameCounts.frame(dropped = false, expectedFrames = FrameCounts.MAX_EXPECTED_FRAMES) }

        assertEquals(0 to 1_800_000_000, counts.counts())
    }

    @Test
    fun `differences stay exact across a wrap of the expected field`() {
        // 1.2M maximal dropped frames: both fields wrap past 2^32
        var start = FrameCounts.ZERO
        repeat(1_200_000) { start += FrameCounts.frame(dropped = true, expectedFrames = FrameCounts.MAX_EXPECTED_FRAMES) }
        val end = start + FrameCounts.frame(dropped = true, expectedFrames = FrameCounts.MAX_EXPECTED_FRAMES)

        assertEquals(3599 to 3600, (end - start).counts())
    }
}

/** The counts as a `dropped to expected` pair. */
internal fun FrameCounts.counts(): Pair<Int, Int> = dropped to expected
