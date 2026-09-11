package io.embrace.analysis.fixtures

import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Access to the frozen goldens under `fixtures/goldens/` and the comparison idioms the
 * goldens' manifest prescribes: exact where the golden did no accumulation, relative 1e-9 where it
 * summed with `statistics.mean`, relative 1e-12 for a single `sqrt`/`log`.
 */
object Goldens {

    const val SUMMATION_TOLERANCE = 1e-9
    const val LIBM_TOLERANCE = 1e-12

    fun json(name: String): JsonObject =
        StartupJson.parseToJsonElement(
            Fixtures.path("goldens/$name").readText(),
        ).jsonObject

    fun clusters(el: JsonElement): List<List<Double>> =
        (el as JsonArray).map { row -> doubles(row) }

    fun doubles(el: JsonElement): List<Double> = (el as JsonArray).map { it.jsonPrimitive.double }

    /** A golden number that may be JSON null, or the string "NaN" the producer used for `float('nan')`. */
    fun doubleOrNull(el: JsonElement?): Double? = when {
        el == null || el is JsonNull -> null
        el is JsonPrimitive && el.isString && el.content == "NaN" -> Double.NaN
        else -> el.jsonPrimitive.double
    }

    fun assertClose(label: String, want: Double, got: Double, rel: Double = SUMMATION_TOLERANCE) {
        if (want.isNaN()) {
            assertTrue("$label: want NaN got $got", got.isNaN())
            return
        }
        val tol = rel * maxOf(1.0, kotlin.math.abs(want))
        assertTrue("$label: want $want got $got (tol $tol)", kotlin.math.abs(want - got) <= tol)
    }

    fun assertCloseOrNull(label: String, want: JsonElement?, got: Double?, rel: Double = SUMMATION_TOLERANCE) {
        val w = doubleOrNull(want)
        if (w == null) {
            assertEquals("$label: want null", null, got)
        } else {
            assertNotNull("$label: want $w got null", got)
            assertClose(label, w, got!!, rel)
        }
    }

    fun assertExactOrNull(label: String, want: JsonElement?, got: Double?) {
        val w = doubleOrNull(want)
        if (w == null) {
            assertEquals("$label: want null", null, got)
        } else {
            assertNotNull("$label: want $w got null", got)
            assertEquals(label, w, got!!, 0.0)
        }
    }
}
