package io.embrace.startup.core.stats

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity for the quantile helpers against `stats_synthetic.json`, the frozen golden over recorded
 * inputs. Type-7 values are compared exactly: the port preserves the
 * operation order `lo * (1 - frac) + hi * frac`, so any difference is a real defect, not rounding.
 */
class QuantileTest {

    private val golden = StartupJson.parseToJsonElement(
        SchemaRoundTripTest.fixturesRoot().resolve("goldens/stats_synthetic.json").readText(),
    ).jsonObject
    private val inputs = golden.getValue("inputs").jsonObject

    private fun flat(name: String): List<Double> =
        inputs.getValue(name).jsonArray.map { it.jsonPrimitive.double }

    @Test
    fun `type7 quantile matches the golden on both arms at every recorded p`() {
        val q = golden.getValue("quantile").jsonObject
        mapOf("a_flat" to flat("a_flat_sorted"), "b_flat" to flat("b_flat_sorted")).forEach { (key, sorted) ->
            q.getValue(key).jsonObject.forEach { (pStr, v) ->
                val p = pStr.toDouble()
                assertEquals("$key p=$pStr", v.jsonPrimitive.double, Quantile.type7(sorted, p), 0.0)
            }
        }
    }

    @Test
    fun `type7 handles the empty and singleton edges like the golden`() {
        assertEquals(true, Quantile.type7(emptyList(), 0.5).isNaN())
        assertEquals(7.25, Quantile.type7(listOf(7.25), 0.9), 0.0)
    }

    @Test
    fun `quantile support matches, including half-to-even rounding`() {
        golden.getValue("quantile_support").jsonObject.forEach { (nStr, byP) ->
            byP.jsonObject.forEach { (pStr, v) ->
                assertEquals("n=$nStr p=$pStr", v.jsonPrimitive.int, Quantile.support(nStr.toInt(), pStr.toDouble()))
            }
        }
    }

    @Test
    fun `quantile CI trustworthiness matches the golden table and its default`() {
        golden.getValue("quantile_ci_trustworthy").jsonObject.forEach { (nStr, byP) ->
            byP.jsonObject.forEach { (pStr, v) ->
                assertEquals(
                    "n=$nStr p=$pStr",
                    v.jsonPrimitive.boolean,
                    Quantile.ciTrustworthy(nStr.toInt(), pStr.toDouble()),
                )
            }
        }
    }

    @Test
    fun `python median averages the middle pair for even n`() {
        assertEquals(2.5, Quantile.median(listOf(1.0, 2.0, 3.0, 4.0)), 0.0)
        assertEquals(2.0, Quantile.median(listOf(1.0, 2.0, 3.0)), 0.0)
    }
}
