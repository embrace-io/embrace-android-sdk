package io.embrace.android.embracesdk.internal.vitals

import org.junit.Assert.assertEquals
import org.junit.Test

internal class FrameCountsTest {

    @Test
    fun `zero has no frames`() {
        assertEquals(0L to 0L, FrameCounts.ZERO.counts())
    }

    @Test
    fun `a rendered frame is one expected frame and none dropped`() {
        assertEquals(0L to 1L, FrameCounts.frame(dropped = false, expectedFrames = 1).counts())
    }

    @Test
    fun `a dropped frame drops every vsync it spanned except the one it appeared on`() {
        // 60Hz, 0.5s over its deadline: 31 vsyncs, 30 of which showed nothing new
        assertEquals(30L to 31L, FrameCounts.frame(dropped = true, expectedFrames = 31).counts())
    }

    @Test
    fun `dropped and expected frames accumulate independently`() {
        val counts = FrameCounts.ZERO +
            FrameCounts.frame(dropped = false, expectedFrames = 1) +
            FrameCounts.frame(dropped = true, expectedFrames = 3) +
            FrameCounts.frame(dropped = true, expectedFrames = 61)

        assertEquals(62L to 65L, counts.counts())
    }

    @Test
    fun `overlapping intervals each get their own counts`() {
        val outerStart = FrameCounts.ZERO
        val innerStart = outerStart + FrameCounts.frame(dropped = true, expectedFrames = 2)
        val end = innerStart + FrameCounts.frame(dropped = false, expectedFrames = 1)

        assertEquals(0L to 1L, (end - innerStart).counts())
        assertEquals(1L to 3L, (end - outerStart).counts())
    }

    @Test
    fun `a frame spanning fewer than one vsync is clamped to one`() {
        assertEquals(0L to 1L, FrameCounts.frame(dropped = true, expectedFrames = 0).counts())
        assertEquals(0L to 1L, FrameCounts.frame(dropped = true, expectedFrames = -5).counts())
    }

    @Test
    fun `counts past the signed Int range read as unsigned`() {
        val counts = FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE) +
            FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE)

        assertEquals(0xFFFF_FFFCL to 0xFFFF_FFFEL, counts.counts())
    }

    @Test
    fun `a saturated expected count does not carry into dropped`() {
        var counts = FrameCounts.ZERO
        repeat(3) { counts += FrameCounts.frame(dropped = false, expectedFrames = Int.MAX_VALUE) }

        assertEquals(0L to 0xFFFF_FFFFL, counts.counts())
    }

    @Test
    fun `saturated counts never report more dropped than expected`() {
        var counts = FrameCounts.ZERO
        repeat(3) { counts += FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE) }

        assertEquals(0xFFFF_FFFFL to 0xFFFF_FFFFL, counts.counts())
    }

    @Test
    fun `a reversed difference is zero rather than borrowing from dropped`() {
        val start = FrameCounts.frame(dropped = false, expectedFrames = 2)
        val end = FrameCounts.frame(dropped = false, expectedFrames = 3)

        assertEquals(0L to 0L, (start - end).counts())
    }

    @Test
    fun `a corrupt difference never reports more dropped than expected`() {
        val start = FrameCounts.frame(dropped = false, expectedFrames = 3)
        val end = FrameCounts.frame(dropped = true, expectedFrames = 3)

        assertEquals(0L to 0L, (end - start).counts())
    }

    @Test
    fun `differences stay exact below saturation`() {
        val start = FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE)
        val end = start + FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE - 1L to Int.MAX_VALUE.toLong(), (end - start).counts())
    }

    @Test
    fun `frames counted after saturation read as none`() {
        var start = FrameCounts.ZERO
        repeat(3) { start += FrameCounts.frame(dropped = true, expectedFrames = Int.MAX_VALUE) }
        val end = start + FrameCounts.frame(dropped = true, expectedFrames = 2)

        assertEquals(0L to 0L, (end - start).counts())
    }
}

/** The counts as a `dropped to expected` pair. */
internal fun FrameCounts.counts(): Pair<Long, Long> = dropped to expected
