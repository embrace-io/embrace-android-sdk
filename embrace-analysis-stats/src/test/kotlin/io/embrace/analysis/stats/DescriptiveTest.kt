package io.embrace.analysis.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * [Descriptive] against the failure modes it exists to avoid: naive double summation drifting on a
 * long run of `0.1`s, and the documented NaN/undefined edges for correlation and sample variance.
 */
class DescriptiveTest {

    @Test
    fun `exactMean is correctly rounded where naive summation drifts`() {
        val tenths = List(REPEAT_COUNT) { 0.1 }
        val naive = tenths.sum() / tenths.size

        val exact = Descriptive.exactMean(tenths)

        assertEquals(0.1, exact, 0.0)
        assertTrue("naive summation was expected to drift from 0.1, got $naive", naive != 0.1)
    }

    @Test
    fun `pearson is NaN on zero variance and on an empty series`() {
        assertTrue(Descriptive.pearson(listOf(3.0, 3.0, 3.0), listOf(1.0, 2.0, 3.0)).isNaN())
        assertTrue(Descriptive.pearson(emptyList(), emptyList()).isNaN())
    }

    @Test
    fun `pearson requires equal length series`() {
        val error = runCatching { Descriptive.pearson(listOf(1.0, 2.0), listOf(1.0)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `sampleStdev requires at least two values`() {
        val error = runCatching { Descriptive.sampleStdev(listOf(1.0)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `sampleStdev matches the textbook n minus one formula on a known dataset`() {
        val stdev = Descriptive.sampleStdev(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))

        assertEquals(sqrt(32.0 / 7.0), stdev, 1e-12)
    }

    private companion object {
        const val REPEAT_COUNT = 10_000
    }
}
