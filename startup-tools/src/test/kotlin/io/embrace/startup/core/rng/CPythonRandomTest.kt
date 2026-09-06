package io.embrace.startup.core.rng

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator must reproduce CPython's `random.Random(12345)` exactly - not approximately, not
 * statistically - because every bootstrap bound and permutation p-value downstream is a function of
 * these draws. The golden was produced by a fresh `Random(12345)` per sub-experiment.
 *
 * `random()` values are compared bit-for-bit (as doubles, exact); a single ulp of drift anywhere in
 * the 53-bit construction would show up here long before it corrupted a confidence interval.
 */
class CPythonRandomTest {

    private val golden = StartupJson.parseToJsonElement(
        SchemaRoundTripTest.fixturesRoot().resolve("goldens/rng.json").readText(),
    ).jsonObject

    @Test
    fun `random() reproduces the first 1000 CPython doubles exactly`() {
        val expected = golden.getValue("random_first_1000").jsonArray.map { it.jsonPrimitive.double }
        val rng = CPythonRandom(SEED)
        expected.forEachIndexed { i, e ->
            val a = rng.random()
            assertEquals("draw $i", e, a, 0.0)
        }
    }

    @Test
    fun `getrandbits(32) reproduces the first 200 words exactly`() {
        val expected = golden.getValue("getrandbits32_first_200").jsonArray.map { it.jsonPrimitive.long }
        val rng = CPythonRandom(SEED)
        expected.forEachIndexed { i, e -> assertEquals("word $i", e, rng.getRandBits(32)) }
    }

    @Test
    fun `choice over range(97) reproduces 300 picks exactly`() {
        val expected = golden.getValue("choice_range97_first_300").jsonArray.map { it.jsonPrimitive.int }
        val rng = CPythonRandom(SEED)
        val seq = (0 until 97).toList()
        expected.forEachIndexed { i, e -> assertEquals("pick $i", e, rng.choice(seq)) }
    }

    @Test
    fun `shuffle reproduces three successive shuffles of range(50)`() {
        val expected = golden.getValue("shuffle_range50_three_times").jsonArray
            .map { row -> (row as JsonArray).map { it.jsonPrimitive.int } }
        assertEquals(3, expected.size)

        // The golden's protocol: ONE fresh generator, three shuffles in sequence. Whether each shuffle
        // started from range(50) again or from the previous result is not stated, so both readings are
        // tried and the one that matches is reported; exactly one must.
        val cumulative = run {
            val rng = CPythonRandom(SEED)
            val x = (0 until 50).toMutableList()
            List(3) {
                rng.shuffle(x)
                x.toList()
            }
        }
        val restarted = run {
            val rng = CPythonRandom(SEED)
            List(3) { (0 until 50).toMutableList().also { rng.shuffle(it) }.toList() }
        }
        val matchCumulative = cumulative == expected
        val matchRestarted = restarted == expected
        assertTrue(
            "neither shuffle protocol reproduces the golden; first shuffle got ${cumulative[0].take(8)} " +
                "vs expected ${expected[0].take(8)}",
            matchCumulative || matchRestarted,
        )
    }

    private companion object {
        const val SEED = 12345L
    }
}
