package io.embrace.android.embracesdk.internal.perfetto.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class PercentilesTest {

    @Test
    fun `a percentile is a duration the trace recorded, taken by nearest rank`() {
        val durations = longArrayOf(100, 200, 300, 400)
        assertEquals(Percentile(50, 200), percentile(durations, 50))
        assertEquals(Percentile(90, 400), percentile(durations, 90))
        assertEquals(Percentile(100, 400), percentile(durations, 100))
    }

    @Test
    fun `the rank stays exact, where dividing before multiplying would not`() {
        assertEquals(Percentile(60, 300), percentile(longArrayOf(100, 200, 300, 400, 500), 60))
        assertEquals(Percentile(70, 700), percentile(LongArray(10) { (it + 1) * 100L }, 70))
    }

    @Test
    fun `a rank below the first clamps to the shortest duration`() {
        assertEquals(Percentile(0, 100), percentile(longArrayOf(100, 200, 300, 400), 0))
    }

    @Test
    fun `one duration is every percentile of itself`() {
        DEFAULT_PERCENTILES.forEach { rank ->
            assertEquals(Percentile(rank, 400), percentile(longArrayOf(400), rank))
        }
    }

    @Test
    fun `there is no percentile of nothing, which is a caller error rather than a value`() {
        assertThrows(IllegalArgumentException::class.java) { percentile(longArrayOf(), 50) }
    }
}
