package io.embrace.analysis.stats

import io.embrace.analysis.fixtures.Goldens
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TOST equivalence against `stats_synthetic.json`. The relative CI is the exact bootstrap CI divided
 * by a Type-7 median, so it is compared at the summation tolerance the manifest prescribes.
 */
class EquivalenceTest {

    private val golden = Goldens.json("stats_synthetic.json")
    private val inputs = golden.getValue("inputs").jsonObject
    private val a = Goldens.clusters(inputs.getValue("a_clusters"))
    private val b = Goldens.clusters(inputs.getValue("b_clusters"))
    private val tost = golden.getValue("tost_equivalence").jsonObject

    @Test
    fun `relative CI and verdict match at 10 percent and 2 percent margins`() {
        listOf("margin_10" to 10.0, "margin_2" to 2.0).forEach { (key, margin) ->
            val want = tost.getValue(key).jsonObject
            val got = Equivalence.tost(a, b, margin)
            assertTrue(key, got.available)
            assertEquals(key, want.getValue("equivalent").jsonPrimitive.boolean, got.equivalent)
            assertEquals(key, want.getValue("margin_pct").jsonPrimitive.double, got.marginPct!!, 0.0)
            val ci = want.getValue("ci_pct").jsonArray.map { it.jsonPrimitive.double }
            Goldens.assertClose("$key lo", ci[0], got.ciPct!!.first)
            Goldens.assertClose("$key hi", ci[1], got.ciPct!!.second)
        }
    }

    @Test
    fun `too few clusters and a zero baseline are reported unavailable with the golden reasons`() {
        val few = Equivalence.tost(a.take(2), b.take(2), 5.0)
        assertFalse(few.available)
        assertNull(few.equivalent)
        assertEquals(tost.getValue("too_few_clusters").jsonObject.getValue("reason").jsonPrimitive.content, few.reason)

        val zeros = List(a.size) { List(a[0].size) { 0.0 } }
        val zero = Equivalence.tost(zeros, b, 5.0)
        assertFalse(zero.available)
        assertNull(zero.equivalent)
        assertEquals(tost.getValue("zero_baseline").jsonObject.getValue("reason").jsonPrimitive.content, zero.reason)
    }
}
