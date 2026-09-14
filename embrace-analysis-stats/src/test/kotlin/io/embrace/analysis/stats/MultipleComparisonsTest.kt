package io.embrace.analysis.stats

import io.embrace.analysis.fixtures.Goldens
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Benjamini–Hochberg verdicts against `stats_synthetic.json`, including null entries in the family. */
class MultipleComparisonsTest {

    private val golden = Goldens.json("stats_synthetic.json").getValue("benjamini_hochberg").jsonObject
    private val inputs = golden.getValue("inputs").jsonArray.map { el ->
        if (el is JsonNull) null else el.jsonPrimitive.double
    }

    @Test
    fun `verdicts match at alpha 0_05 and 0_10 with a null p-value excluded from the family`() {
        assertEquals(verdicts("alpha_0.05"), MultipleComparisons.benjaminiHochberg(inputs))
        assertEquals(verdicts("alpha_0.10"), MultipleComparisons.benjaminiHochberg(inputs, alpha = 0.10))
    }

    @Test
    fun `an all-null family stays null and an empty family stays empty`() {
        assertEquals(listOf(null, null), MultipleComparisons.benjaminiHochberg(listOf(null, null)))
        assertEquals(emptyList<Boolean?>(), MultipleComparisons.benjaminiHochberg(emptyList()))
    }

    private fun verdicts(key: String): List<Boolean?> =
        golden.getValue(key).jsonArray.map { el -> if (el is JsonNull) null else el.jsonPrimitive.boolean }
}
