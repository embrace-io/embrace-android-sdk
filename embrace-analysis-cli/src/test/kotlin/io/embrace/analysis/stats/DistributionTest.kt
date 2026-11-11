package io.embrace.analysis.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class DistributionTest {

    @Test
    fun `count, min and max come from the values in any order`() {
        val distribution = Distribution(listOf(3.0, 1.0, 2.0))
        assertEquals(3, distribution.count)
        assertEquals(1.0, distribution.min, 0.0)
        assertEquals(3.0, distribution.max, 0.0)
    }

    @Test
    fun `the median of an odd count is the middle value`() {
        assertEquals(2.0, Distribution(listOf(3.0, 1.0, 2.0)).median, 0.0)
        assertEquals(5.0, Distribution(listOf(5.0)).median, 0.0)
    }

    @Test
    fun `the median of an even count is the mean of the two middle values`() {
        assertEquals(2.5, Distribution(listOf(4.0, 1.0, 3.0, 2.0)).median, 0.0)
    }

    @Test
    fun `the mean is the average of the values`() {
        assertEquals(2.5, Distribution(listOf(1.0, 4.0, 2.0, 3.0)).mean, 0.0)
        assertEquals(1.5, Distribution(listOf(1.0, 2.0)).mean, 0.0)
    }

    @Test
    fun `a distribution needs a value`() {
        assertThrows(IllegalArgumentException::class.java) { Distribution(emptyList()) }
    }

    @Test
    fun `the summary reads count, min, median, mean and max to one decimal, or no data`() {
        assertEquals("n=4  min=1.0  median=2.5  mean=2.5  max=4.0", Distribution(listOf(4.0, 1.0, 3.0, 2.0)).summary())
        assertEquals("n=4  min=1.0  median=2.5  mean=2.5  max=4.0", Distribution.summaryOf(listOf(4.0, 1.0, 3.0, 2.0)))
        assertEquals("no data", Distribution.summaryOf(emptyList()))
    }
}
