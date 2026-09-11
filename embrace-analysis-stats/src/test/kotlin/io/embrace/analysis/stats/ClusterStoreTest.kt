package io.embrace.analysis.stats

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The method of record on the REAL data: four version pairs from the longitudinal store, each arm
 * chunked into 10 passes of 20 exactly as the golden did, then bootstrap CIs (median, p90, p95),
 * permutation p-values (median, p90), ICC and DEFF - compared to what the golden produced.
 *
 * These are the comparisons behind the published version tables. Reproducing them to the last digit
 * is the single strongest statement the port can make about having carried the method over intact.
 * The flagship 9.1.0 pair is recorded as skipped (198 windows; positional pass recovery would
 * misalign), and this test asserts that it carries no results rather than silently passing over it.
 */
class ClusterStoreTest {

    private val golden = StartupJson.parseToJsonElement(
        Fixtures.root().resolve("goldens/stats_store.json").readText(),
    ).jsonObject
    private val pairs = golden.getValue("pairs").jsonObject

    @Test
    fun `all four testable pairs reproduce bootstrap, permutation, ICC and DEFF`() {
        val testable = pairs.filterKeys { !it.contains("skip") }
        assertEquals(4, testable.size)
        testable.forEach { (name, pairEl) ->
            val pair = pairEl.jsonObject
            val a = clusters(pair.getValue("a_clusters"))
            val b = clusters(pair.getValue("b_clusters"))
            assertEquals("$name a clusters", 10, a.size)
            assertEquals("$name b clusters", 10, b.size)

            val boot = pair.getValue("cluster_bootstrap_diff").jsonObject
            assertBootstrapExact("$name median", boot.getValue("median"), Cluster.bootstrapDiff(a, b))
            assertBootstrapExact("$name p90", boot.getValue("p90"), Cluster.bootstrapDiff(a, b, Cluster.Statistic.Quantile(0.90)))
            assertBootstrapExact("$name p95", boot.getValue("p95"), Cluster.bootstrapDiff(a, b, Cluster.Statistic.Quantile(0.95)))

            val perm = pair.getValue("cluster_permutation_test").jsonObject
            assertPermutationExact("$name median", perm.getValue("median"), Cluster.permutationTest(a, b))
            assertPermutationExact("$name p90", perm.getValue("p90"), Cluster.permutationTest(a, b, Cluster.Statistic.Quantile(0.90)))

            val de = pair.getValue("design_effect").jsonObject
            val icc = pair.getValue("icc_oneway").jsonObject
            listOf("a" to a, "b" to b).forEach { (arm, cl) ->
                val got = Cluster.designEffect(cl)
                val want = de.getValue(arm).jsonObject
                assertClose("$name $arm icc", icc.getValue(arm).jsonPrimitive.double, got.icc!!)
                assertClose("$name $arm deff", want.getValue("deff").jsonPrimitive.double, got.deff!!)
                assertClose("$name $arm n_eff", want.getValue("n_effective").jsonPrimitive.double, got.nEffective!!)
            }
        }
    }

    @Test
    fun `the 198-window flagship pair is recorded as skipped, not analysed`() {
        val skipped = pairs.filterKeys { it.contains("skip") }
        assertEquals(1, skipped.size)
        val pair = skipped.values.single().jsonObject
        assertTrue("skipped pair must not carry bootstrap results", !pair.containsKey("cluster_bootstrap_diff"))
    }

    private fun assertBootstrapExact(label: String, want: JsonElement, got: Cluster.BootstrapResult) {
        val w = want.jsonObject
        assertTrue("$label available", got.available)
        assertEquals("$label diff", w.getValue("diff").jsonPrimitive.double, got.diff, 0.0)
        val ci = w.getValue("ci").jsonArray.map { it.jsonPrimitive.double }
        assertEquals("$label ci lo", ci[0], got.ci!!.first, 0.0)
        assertEquals("$label ci hi", ci[1], got.ci!!.second, 0.0)
    }

    private fun assertPermutationExact(label: String, want: JsonElement, got: Cluster.PermutationResult) {
        val w = want.jsonObject
        assertTrue("$label available", got.available)
        assertEquals("$label p", w.getValue("p").jsonPrimitive.double, got.p!!, 0.0)
        assertEquals("$label observed", w.getValue("observed").jsonPrimitive.double, got.observed!!, 0.0)
    }

    private fun assertClose(label: String, want: Double, got: Double) {
        val tol = 1e-9 * maxOf(1.0, kotlin.math.abs(want))
        assertTrue("$label: want $want got $got", kotlin.math.abs(want - got) <= tol)
    }

    private fun clusters(el: JsonElement): List<List<Double>> =
        (el as JsonArray).map { row -> (row as JsonArray).map { it.jsonPrimitive.double } }
}
