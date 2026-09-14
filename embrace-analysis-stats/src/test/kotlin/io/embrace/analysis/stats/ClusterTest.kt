package io.embrace.analysis.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two behaviours of [Cluster] the golden-parity [ClusterSyntheticTest] does not exercise: the
 * draw-once refactor in [Cluster.bootstrapDiffs] must be observationally identical to calling
 * [Cluster.bootstrapDiff] once per statistic, and [Cluster.permutationTest] has no mean statistic.
 */
class ClusterTest {

    private val a = listOf(
        listOf(1.0, 2.0, 3.0, 4.0, 5.0),
        listOf(2.0, 3.0, 4.0, 5.0, 6.0),
        listOf(0.0, 1.0, 2.0, 3.0, 4.0),
        listOf(3.0, 4.0, 5.0, 6.0, 7.0),
    )
    private val b = listOf(
        listOf(5.0, 6.0, 7.0, 8.0, 9.0),
        listOf(6.0, 7.0, 8.0, 9.0, 10.0),
        listOf(4.0, 5.0, 6.0, 7.0, 8.0),
        listOf(7.0, 8.0, 9.0, 10.0, 11.0),
    )

    @Test
    fun `bootstrapDiffs matches one bootstrapDiff call per statistic, element for element`() {
        val statistics = listOf(Cluster.Statistic.Median, Cluster.Statistic.Quantile(0.9))

        val combined = Cluster.bootstrapDiffs(a, b, statistics, resamples = RESAMPLES, seed = SEED)
        val separate = statistics.map { statistic -> Cluster.bootstrapDiff(a, b, statistic, RESAMPLES, seed = SEED) }

        assertEquals(separate.size, combined.size)
        combined.indices.forEach { i ->
            assertEquals("diff[$i]", separate[i].diff, combined[i].diff, 0.0)
            assertEquals("available[$i]", separate[i].available, combined[i].available)
            assertEquals("ci lo [$i]", separate[i].ci!!.first, combined[i].ci!!.first, 0.0)
            assertEquals("ci hi [$i]", separate[i].ci!!.second, combined[i].ci!!.second, 0.0)
        }
    }

    @Test
    fun `permutationTest rejects the mean statistic with the documented message`() {
        val error = runCatching { Cluster.permutationTest(a, b, Cluster.Statistic.Mean) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("the permutation test has no mean statistic; use the median or a quantile", error!!.message)
    }

    private companion object {
        const val RESAMPLES = 500
        const val SEED = 777L
    }
}
