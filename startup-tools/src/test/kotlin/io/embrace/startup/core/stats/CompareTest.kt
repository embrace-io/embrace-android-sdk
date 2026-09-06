package io.embrace.startup.core.stats

import io.embrace.startup.Goldens
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full `compare()` bundle against three goldens: the synthetic arms (`stats_synthetic.json`),
 * the four real longitudinal version pairs (`stats_store.json`, labels are run ids) and the X37
 * engine A/B legs (`x37_legs.json`, legs as clusters, labels `compat`/`kotlin`).
 *
 * Every field the Python emitted is checked: n, cluster counts, medians, design effects, Cliff's
 * delta, the median CI, the permutation test, the relative median difference, the noise-band verdict,
 * and each quantile row (values, support, CI or the "point estimate only" refusal). Bootstrap and
 * permutation numbers are exact; ICC/DEFF and the percentage difference at the summation tolerance.
 */
class CompareTest {

    @Test
    fun `synthetic arms - defaults, a 2 percent band with p50 and p90, and the 3v3 refusal path`() {
        val golden = Goldens.json("stats_synthetic.json")
        val inputs = golden.getValue("inputs").jsonObject
        val a = Goldens.clusters(inputs.getValue("a_clusters"))
        val b = Goldens.clusters(inputs.getValue("b_clusters"))
        val g = golden.getValue("compare").jsonObject
        assertReport("default", g.getValue("default").jsonObject, Compare.arms(a, b))
        assertReport(
            "noise2_q50_q90",
            g.getValue("noise2_q50_q90").jsonObject,
            Compare.arms(a, b, noiseBandPct = 2.0, quantiles = listOf(0.5, 0.9)),
        )
        assertReport(
            "too_few_clusters_3v3",
            g.getValue("too_few_clusters_3v3").jsonObject,
            Compare.arms(a.take(3), b.take(3)),
        )
    }

    @Test
    fun `real store pairs - the published version comparisons reproduce field for field`() {
        val pairs = Goldens.json("stats_store.json").getValue("pairs").jsonObject
        val testable = pairs.filterKeys { !it.contains("skip") }
        assertEquals(4, testable.size)
        testable.forEach { (name, pairEl) ->
            val pair = pairEl.jsonObject
            val a = Goldens.clusters(pair.getValue("a_clusters"))
            val b = Goldens.clusters(pair.getValue("b_clusters"))
            val labelA = pair.getValue("a_run_id").jsonPrimitive.content
            val labelB = pair.getValue("b_run_id").jsonPrimitive.content
            val report = Compare.arms(a, b, labelA, labelB)
            assertReport(name, pair.getValue("compare").jsonObject, report)
            // With n = 200 per arm both p90 and p95 clear the trust floor, so their CIs are computed.
            report.quantiles.forEach { row -> assertTrue("$name p${row.p} ci computed", row.ci.available) }
            val cliffs = pair.getValue("cliffs_delta").jsonObject
            Goldens.assertExactOrNull("$name cliffs delta", cliffs["delta"], report.effectSize.delta)
            Goldens.assertExactOrNull("$name cliffs a12", cliffs["a12"], report.effectSize.a12)
        }
    }

    @Test
    fun `X37 legs - the void-run headline reproduces, and 2v1 legs hit every unavailable path on real data`() {
        val golden = Goldens.json("x37_legs.json")
        val groups = golden.getValue("groups").jsonObject
        val compare = golden.getValue("compare").jsonObject
        assertEquals(setOf("entry-a", "mid-a"), compare.keys)
        compare.forEach { (device, spec) ->
            val s = spec.jsonObject
            val a = legWindows(groups.getValue("$device|compat").jsonObject, s.getValue("a_leg_order"))
            val b = legWindows(groups.getValue("$device|kotlin").jsonObject, s.getValue("b_leg_order"))
            assertReport(device, s.getValue("result").jsonObject, Compare.arms(a, b, "compat", "kotlin"))
        }
        // Per-leg summaries: statistics.median and Type-7 q90/q95, exact.
        groups.values.forEach { group ->
            group.jsonObject.getValue("legs").jsonArray.forEach { legEl ->
                val leg = legEl.jsonObject
                val sorted = Goldens.doubles(leg.getValue("windows_ms")).sorted()
                val label = leg.getValue("file").jsonPrimitive.content
                assertEquals("$label n", leg.getValue("n").jsonPrimitive.int, sorted.size)
                assertEquals("$label median", leg.getValue("median").jsonPrimitive.double, Quantile.median(sorted), 0.0)
                assertEquals("$label q90", leg.getValue("q90").jsonPrimitive.double, Quantile.type7(sorted, 0.90), 0.0)
                assertEquals("$label q95", leg.getValue("q95").jsonPrimitive.double, Quantile.type7(sorted, 0.95), 0.0)
            }
        }
        val entry = compare.getValue("entry-a").jsonObject.getValue("result").jsonObject
        assertTrue(entry.getValue("median_ci").jsonObject.getValue("available").jsonPrimitive.boolean.not())
        assertTrue(entry.getValue("permutation").jsonObject.getValue("available").jsonPrimitive.boolean.not())
    }

    private fun legWindows(group: JsonObject, order: kotlinx.serialization.json.JsonElement): List<List<Double>> {
        val byLeg = group.getValue("legs").jsonArray.associate { legEl ->
            val leg = legEl.jsonObject
            leg.getValue("leg").jsonPrimitive.int to Goldens.doubles(leg.getValue("windows_ms"))
        }
        return order.jsonArray.map { byLeg.getValue(it.jsonPrimitive.int) }
    }

    private fun assertReport(label: String, want: JsonObject, got: Compare.Report) {
        val la = got.labels.a
        val lb = got.labels.b
        assertEquals("$label clears_noise_band", want.getValue("clears_noise_band").jsonPrimitive.boolean, got.clearsNoiseBand)
        assertSides("$label n", want.getValue("n").jsonObject, la, lb) { el, side ->
            assertEquals(el.jsonPrimitive.int, if (side == 0) got.n.a else got.n.b)
        }
        assertSides("$label clusters", want.getValue("clusters").jsonObject, la, lb) { el, side ->
            assertEquals(el.jsonPrimitive.int, if (side == 0) got.clusters.a else got.clusters.b)
        }
        assertSides("$label median", want.getValue("median").jsonObject, la, lb) { el, side ->
            assertEquals(el.jsonPrimitive.double, if (side == 0) got.median.a else got.median.b, 0.0)
        }
        assertSides("$label design_effect", want.getValue("design_effect").jsonObject, la, lb) { el, side ->
            assertDesignEffect("$label design_effect[$side]", el.jsonObject, if (side == 0) got.designEffect.a else got.designEffect.b)
        }
        val effect = want.getValue("effect_size").jsonObject
        Goldens.assertExactOrNull("$label delta", effect["delta"], got.effectSize.delta)
        Goldens.assertExactOrNull("$label a12", effect["a12"], got.effectSize.a12)
        assertEquals("$label magnitude", effect.getValue("magnitude").jsonPrimitive.content, got.effectSize.magnitude)
        assertBootstrap("$label median_ci", want.getValue("median_ci").jsonObject, got.medianCi)
        assertPermutation("$label permutation", want.getValue("permutation").jsonObject, got.permutation)
        Goldens.assertClose(
            "$label median_diff_pct",
            checkNotNull(Goldens.doubleOrNull(want.getValue("median_diff_pct"))),
            got.medianDiffPct,
        )
        val quantiles = want.getValue("quantiles").jsonObject
        assertEquals("$label quantile keys", quantiles.keys, got.quantiles.map { it.p.toString() }.toSet())
        got.quantiles.forEach { row ->
            val w = quantiles.getValue(row.p.toString()).jsonObject
            assertEquals("$label p${row.p} $la", w.getValue(la).jsonPrimitive.double, row.value.a, 0.0)
            assertEquals("$label p${row.p} $lb", w.getValue(lb).jsonPrimitive.double, row.value.b, 0.0)
            val support = w.getValue("support").jsonObject
            assertEquals("$label p${row.p} support $la", support.getValue(la).jsonPrimitive.int, row.support.a)
            assertEquals("$label p${row.p} support $lb", support.getValue(lb).jsonPrimitive.int, row.support.b)
            assertBootstrap("$label p${row.p} ci", w.getValue("ci").jsonObject, row.ci)
        }
    }

    private fun assertSides(
        label: String,
        want: JsonObject,
        la: String,
        lb: String,
        check: (kotlinx.serialization.json.JsonElement, Int) -> Unit,
    ) {
        assertEquals("$label keys", setOf(la, lb), want.keys)
        check(want.getValue(la), 0)
        check(want.getValue(lb), 1)
    }

    private fun assertDesignEffect(label: String, want: JsonObject, got: Cluster.DesignEffect) {
        assertEquals("$label n", want.getValue("n").jsonPrimitive.int, got.n)
        Goldens.assertCloseOrNull("$label icc", want["icc"], got.icc)
        Goldens.assertCloseOrNull("$label deff", want["deff"], got.deff)
        Goldens.assertCloseOrNull("$label n_effective", want["n_effective"], got.nEffective)
    }

    private fun assertBootstrap(label: String, want: JsonObject, got: Cluster.BootstrapResult) {
        assertEquals("$label available", want.getValue("available").jsonPrimitive.boolean, got.available)
        assertEquals("$label diff", want.getValue("diff").jsonPrimitive.double, got.diff, 0.0)
        val ci = want.getValue("ci")
        if (ci is JsonNull) {
            assertNull("$label ci", got.ci)
        } else {
            val bounds = ci.jsonArray.map { it.jsonPrimitive.double }
            assertEquals("$label ci lo", bounds[0], got.ci!!.first, 0.0)
            assertEquals("$label ci hi", bounds[1], got.ci!!.second, 0.0)
        }
        assertEquals("$label reason", want["reason"]?.jsonPrimitive?.contentOrNull, got.reason)
    }

    private fun assertPermutation(label: String, want: JsonObject, got: Cluster.PermutationResult) {
        assertEquals("$label available", want.getValue("available").jsonPrimitive.boolean, got.available)
        Goldens.assertExactOrNull("$label p", want["p"], got.p)
        Goldens.assertExactOrNull("$label observed", want["observed"], got.observed)
        assertEquals("$label floor", want.getValue("min_attainable_p").jsonPrimitive.double, got.minAttainableP, 0.0)
        assertEquals("$label reason", want["reason"]?.jsonPrimitive?.contentOrNull, got.reason)
    }
}
