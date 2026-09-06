package io.embrace.startup.core.stats

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.json.StoreRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Derive.of` against `derive_store.json`: the Python `ingest_run.derive()` applied to every store
 * record's stored (rounded) windows, plus edge cases. Compared exactly - `derive` is index-picking
 * and a two-value mean, with no accumulation to drift.
 */
class DeriveTest {

    private val fixtures = SchemaRoundTripTest.fixturesRoot()
    private val golden = StartupJson.parseToJsonElement(
        fixtures.resolve("goldens/derive_store.json").readText(),
    ).jsonObject

    @Test
    fun `every store and sweep record derives exactly as the Python did`() {
        val records = listOf("longitudinal/store.jsonl", "longitudinal/sweep-store.jsonl")
            .flatMap { fixtures.resolve(it).readLines() }
            .filter { it.isNotBlank() }
            .map { StartupJson.decodeFromString(StoreRecord.serializer(), it) }
        val expected = golden.getValue("records").jsonObject
        assertEquals(expected.size, records.size)
        records.forEach { rec ->
            val want = expected.getValue(rec.runId).jsonObject.getValue("derived").jsonObject
            val got = Derive.of(rec.windowsMs)
            assertNotNull(rec.runId, got)
            assertDerived(rec.runId, want, got!!)
        }
    }

    @Test
    fun `edge cases match, including the iqr floor below four values`() {
        val edges = golden.getValue("edge_cases").jsonObject
        assertNull(Derive.of(emptyList()))
        assertDerived("one_value", edges.getValue("one_value").jsonObject, Derive.of(listOf(12.5))!!)
        assertDerived("three_values", edges.getValue("three_values").jsonObject, Derive.of(listOf(1.0, 2.0, 3.0))!!)
        assertDerived("four_values", edges.getValue("four_values").jsonObject, Derive.of(listOf(1.0, 2.0, 3.0, 4.0))!!)
        assertDerived("five_values", edges.getValue("five_values").jsonObject, Derive.of(listOf(1.0, 2.0, 3.0, 4.0, 5.0))!!)
    }

    private fun assertDerived(label: String, want: JsonObject, got: io.embrace.startup.core.json.Derived) {
        assertEquals("$label n", want.getValue("n").jsonPrimitive.int, got.n)
        assertEquals("$label median", want.getValue("median").jsonPrimitive.double, got.median, 0.0)
        assertEquals("$label p90", want.getValue("p90").jsonPrimitive.double, got.p90, 0.0)
        assertEquals("$label p95", want.getValue("p95").jsonPrimitive.double, got.p95!!, 0.0)
        assertEquals("$label max", want.getValue("max").jsonPrimitive.double, got.max, 0.0)
        assertEquals("$label iqr", want.getValue("iqr").jsonPrimitive.double, got.iqr, 0.0)
    }
}
