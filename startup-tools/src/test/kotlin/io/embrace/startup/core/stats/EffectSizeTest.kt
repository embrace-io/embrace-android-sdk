package io.embrace.startup.core.stats

import io.embrace.startup.Goldens
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cliff's delta and its clustering caveat against `stats_synthetic.json`; all exact. */
class EffectSizeTest {

    private val golden = Goldens.json("stats_synthetic.json")
    private val inputs = golden.getValue("inputs").jsonObject
    private val aFlat = Goldens.doubles(inputs.getValue("a_flat_sorted"))
    private val bFlat = Goldens.doubles(inputs.getValue("b_flat_sorted"))

    @Test
    fun `cliffs delta and A12 match exactly in both directions, for identical and empty arms`() {
        val g = golden.getValue("cliffs_delta").jsonObject
        assertDelta("a_vs_b", g, EffectSize.cliffsDelta(aFlat, bFlat))
        assertDelta("b_vs_a", g, EffectSize.cliffsDelta(bFlat, aFlat))
        assertDelta("identical", g, EffectSize.cliffsDelta(aFlat, aFlat))
        assertDelta("empty_a", g, EffectSize.cliffsDelta(emptyList(), bFlat))
        assertDelta(
            "small_[1,2,3]_vs_[2,3,4]",
            g,
            EffectSize.cliffsDelta(listOf(1.0, 2.0, 3.0), listOf(2.0, 3.0, 4.0)),
        )
    }

    @Test
    fun `caveat text reproduces the golden wording at every ICC band`() {
        val g = golden.getValue("cliffs_delta_caveat").jsonObject
        assertEquals(g.getValue("None").jsonPrimitive.content, EffectSize.cliffsDeltaCaveat(null))
        listOf(0.05, 0.1, 0.2, 0.3, 0.65).forEach { icc ->
            assertEquals(g.getValue(icc.toString()).jsonPrimitive.content, EffectSize.cliffsDeltaCaveat(icc))
        }
    }

    private fun assertDelta(key: String, g: kotlinx.serialization.json.JsonObject, got: EffectSize.CliffsDelta) {
        val want = g.getValue(key).jsonObject
        Goldens.assertExactOrNull("$key delta", want["delta"], got.delta)
        Goldens.assertExactOrNull("$key a12", want["a12"], got.a12)
        assertEquals("$key magnitude", want.getValue("magnitude").jsonPrimitive.contentOrNull, got.magnitude)
    }
}
