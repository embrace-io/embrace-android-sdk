package io.embrace.startup.core.stats

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cluster inference against `stats_synthetic.json`: two recorded arms of 5 clusters × 8 values,
 * bootstrap and permutation at the golden's defaults (seed 12345, 10 000 resamples).
 *
 * Bootstrap bounds and permutation p-values are asserted EXACTLY. They are quantiles over sorted
 * resamples of medians, so with an identical RNG there is nothing to drift; a one-ulp difference
 * here would mean a draw was consumed in a different order. ICC and DEFF are compared at relative
 * 1e-9 because Python's `statistics.mean` sums exactly and this port sums doubles.
 */
class ClusterSyntheticTest {

    private val golden = StartupJson.parseToJsonElement(
        SchemaRoundTripTest.fixturesRoot().resolve("goldens/stats_synthetic.json").readText(),
    ).jsonObject
    private val inputs = golden.getValue("inputs").jsonObject
    private val a = clusters(inputs.getValue("a_clusters"))
    private val b = clusters(inputs.getValue("b_clusters"))

    @Test
    fun `bootstrap CI matches exactly for median, p90, p95 and a wider alpha`() {
        val g = golden.getValue("cluster_bootstrap_diff").jsonObject
        assertBootstrap(g.getValue("median_default"), Cluster.bootstrapDiff(a, b))
        assertBootstrap(g.getValue("quantile_p90"), Cluster.bootstrapDiff(a, b, Cluster.Statistic.Quantile(0.90)))
        assertBootstrap(g.getValue("quantile_p95"), Cluster.bootstrapDiff(a, b, Cluster.Statistic.Quantile(0.95)))
        assertBootstrap(g.getValue("alpha_0.10"), Cluster.bootstrapDiff(a, b, alpha = 0.10))
    }

    @Test
    fun `bootstrap CI for the mean matches within the summation tolerance`() {
        val want = golden.getValue("cluster_bootstrap_diff").jsonObject.getValue("mean").jsonObject
        val got = Cluster.bootstrapDiff(a, b, Cluster.Statistic.Mean)
        assertTrue(got.available)
        assertClose("mean diff", want.getValue("diff").jsonPrimitive.double, got.diff)
        val ci = want.getValue("ci").jsonArray.map { it.jsonPrimitive.double }
        assertClose("mean ci lo", ci[0], got.ci!!.first)
        assertClose("mean ci hi", ci[1], got.ci!!.second)
    }

    @Test
    fun `bootstrap with three clusters per arm is reported unavailable, with the observed diff`() {
        val want = golden.getValue("cluster_bootstrap_diff").jsonObject.getValue("too_few_clusters_3v3").jsonObject
        val got = Cluster.bootstrapDiff(a.take(3), b.take(3))
        assertFalse(got.available)
        assertNull(got.ci)
        assertEquals(want.getValue("diff").jsonPrimitive.double, got.diff, 0.0)
        assertFalse(want.getValue("available").jsonPrimitive.boolean)
    }

    @Test
    fun `permutation p-values match exactly for median and p90`() {
        val g = golden.getValue("cluster_permutation_test").jsonObject
        assertPermutation(g.getValue("median_default"), Cluster.permutationTest(a, b))
        assertPermutation(g.getValue("quantile_p90"), Cluster.permutationTest(a, b, Cluster.Statistic.Quantile(0.90)))
    }

    @Test
    fun `permutation floors are reported when clusters are too few`() {
        val g = golden.getValue("cluster_permutation_test").jsonObject
        val one = Cluster.permutationTest(a.take(1), b.take(1))
        assertFalse(one.available)
        assertEquals(
            g.getValue("too_few_clusters_1v1").jsonObject.getValue("min_attainable_p").jsonPrimitive.double,
            one.minAttainableP,
            0.0,
        )
        val three = Cluster.permutationTest(a.take(3), b.take(3))
        assertFalse(three.available)
        assertEquals(
            g.getValue("too_few_clusters_3v3").jsonObject.getValue("min_attainable_p").jsonPrimitive.double,
            three.minAttainableP,
            0.0,
        )
    }

    @Test
    fun `ICC and design effect match within tolerance, and the null branches hold`() {
        val icc = golden.getValue("icc_oneway").jsonObject
        assertClose("icc a", icc.getValue("a").jsonPrimitive.double, Cluster.iccOneway(a)!!)
        assertClose("icc b", icc.getValue("b").jsonPrimitive.double, Cluster.iccOneway(b)!!)
        assertTrue(icc.getValue("single_cluster") is JsonNull)
        assertNull(Cluster.iccOneway(listOf(a[0])))
        assertNull(Cluster.iccOneway(listOf(listOf(1.0), listOf(2.0), listOf(3.0))))
        assertNull(Cluster.iccOneway(listOf(listOf(5.0, 5.0, 5.0), listOf(5.0, 5.0, 5.0))))

        val de = golden.getValue("design_effect").jsonObject
        listOf("a" to a, "b" to b).forEach { (key, arm) ->
            val want = de.getValue(key).jsonObject
            val got = Cluster.designEffect(arm)
            assertEquals(want.getValue("n").jsonPrimitive.int, got.n)
            assertClose("$key icc", want.getValue("icc").jsonPrimitive.double, got.icc!!)
            assertClose("$key deff", want.getValue("deff").jsonPrimitive.double, got.deff!!)
            assertClose("$key n_eff", want.getValue("n_effective").jsonPrimitive.double, got.nEffective!!)
        }
        val empty = Cluster.designEffect(emptyList())
        assertEquals(0, empty.n)
        assertNull(empty.icc)
    }

    private fun assertBootstrap(want: kotlinx.serialization.json.JsonElement, got: Cluster.BootstrapResult) {
        val w = want.jsonObject
        assertTrue(got.available)
        assertEquals(w.getValue("diff").jsonPrimitive.double, got.diff, 0.0)
        val ci = w.getValue("ci").jsonArray.map { it.jsonPrimitive.double }
        assertEquals("ci lo", ci[0], got.ci!!.first, 0.0)
        assertEquals("ci hi", ci[1], got.ci!!.second, 0.0)
    }

    private fun assertPermutation(want: kotlinx.serialization.json.JsonElement, got: Cluster.PermutationResult) {
        val w = want.jsonObject
        assertTrue(got.available)
        assertEquals("p", w.getValue("p").jsonPrimitive.double, got.p!!, 0.0)
        assertEquals("observed", w.getValue("observed").jsonPrimitive.double, got.observed!!, 0.0)
        assertEquals("floor", w.getValue("min_attainable_p").jsonPrimitive.double, got.minAttainableP, 0.0)
    }

    private fun assertClose(label: String, want: Double, got: Double) {
        val tol = 1e-9 * maxOf(1.0, kotlin.math.abs(want))
        assertTrue("$label: want $want got $got", kotlin.math.abs(want - got) <= tol)
    }

    private fun clusters(el: kotlinx.serialization.json.JsonElement): List<List<Double>> =
        (el as JsonArray).map { row -> (row as JsonArray).map { it.jsonPrimitive.double } }
}
