package io.embrace.startup.core.json

import io.embrace.startup.core.io.Zips
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Phase-1a gate for the core contract: every real fixture the Python produced decodes into the
 * Kotlin schemas, and a decoded store record re-encodes to a tree equivalent to the original.
 *
 * "Equivalent" is deliberate. kotlinx-serialization cannot reproduce Python's `sort_keys=True`
 * byte layout, and Python emits `1.0` where Kotlin may emit `1.0` or `1` depending on the type, so
 * the comparison is over parsed JSON trees with numbers compared as doubles - the same rule the
 * golden-fixture tests use. A schema that silently drops a field fails here, because the re-encoded
 * tree is missing a key the original had.
 */
class SchemaRoundTripTest {

    private val fixtures: File = fixturesRoot()

    @Test
    fun `every longitudinal store record decodes`() {
        val records = readJsonl(fixtures.resolve("longitudinal/store.jsonl"))
        assertEquals(12, records.size)
        records.forEach { rec ->
            // The flagship's 9.1.0 arm is known to hold 198 windows (two dropped traces); the store's
            // own invariant is that `derived.n` counts exactly the windows present.
            assertEquals(rec.runId, rec.derived.n, rec.windowsMs.size)
            assertTrue(rec.runId, rec.windowsMs.size in 198..200)
            assertNotNull(rec.sdkVersion)
            assertTrue(rec.runId, rec.deviceProfile.isComplete)
        }
    }

    @Test
    fun `the salvaged sweep records decode despite their null fields`() {
        val records = readJsonl(fixtures.resolve("longitudinal/sweep-store.jsonl"))
        assertEquals(4, records.size)
        // Three were salvaged with sdk_version/build_type/compile_state null and an empty device
        // profile; one is a real 8.3.0 record. Reading them is required; writing them is not allowed.
        assertEquals(3, records.count { it.sdkVersion == null })
        assertEquals(3, records.count { !it.deviceProfile.isComplete })
        assertEquals(1, records.count { it.sdkVersion == "8.3.0" && it.deviceProfile.isComplete })
        records.forEach { assertEquals(it.runId, it.derived.n, it.windowsMs.size) }
    }

    @Test
    fun `store records re-encode to an equivalent tree`() {
        val text = fixtures.resolve("longitudinal/store.jsonl").readLines().filter { it.isNotBlank() }
        text.forEach { line ->
            val original = StartupJson.parseToJsonElement(line).jsonObject
            val decoded = StartupJson.decodeFromString(StoreRecord.serializer(), line)
            val reencoded = StartupJson.encodeToJsonElement(StoreRecord.serializer(), decoded).jsonObject
            assertTreesEquivalent(original, reencoded, path = decoded.runId)
        }
    }

    @Test
    fun `the reference set decodes with all four devices`() {
        val set = StartupJson.decodeFromString(
            ReferenceSet.serializer(),
            fixtures.resolve("longitudinal/reference-set.json").readText(),
        )
        assertEquals(setOf("flagship-a", "mid-a", "mid-b", "entry-a"), set.devices.keys)
        assertEquals("composed", set.recipe.instrument)
        assertEquals(200, set.recipe.runShape.expectedWindows)
    }

    @Test
    fun `every section-medians file decodes and carries a window`() {
        val files = listOf("sections/x35", "sections/x34").flatMap { dir ->
            fixtures.resolve(dir).listFiles { f -> f.name.endsWith(".json") }.orEmpty().toList()
        }
        assertEquals(14, files.size)
        files.forEach { f ->
            val s = StartupJson.decodeFromString(SectionMedians.serializer(), f.readText())
            assertNotNull("no __window__ in ${f.name}", s.windowMs)
            assertTrue(s.sections.isNotEmpty())
        }
    }

    @Test
    fun `every preserved leg decodes at its declared shape`() {
        val legs = Zips.unpackToTemp(fixtures.toPath().resolve("legs").resolve("x37.zip")).toFile()
        val files = legs.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        assertTrue(files.size >= 22)
        files.forEach { f ->
            val leg = StartupJson.decodeFromString(LegRecord.serializer(), f.readText())
            assertEquals(f.name, leg.iterationsDeclared, leg.windowsMs.size)
            assertTrue(leg.arm == "compat" || leg.arm == "kotlin")
        }
    }

    private fun readJsonl(file: File): List<StoreRecord> =
        file.readLines().filter { it.isNotBlank() }
            .map { StartupJson.decodeFromString(StoreRecord.serializer(), it) }

    /**
     * Absent and null are the same thing in this contract (`StartupJson` sets `explicitNulls = false`),
     * because Python wrote `"app_build_id": null` where Kotlin omits the key; both mean "no value".
     * So null-valued keys are dropped from both sides before key sets are compared.
     */
    private fun assertTreesEquivalent(expected: JsonElement, actual: JsonElement, path: String) {
        when {
            expected is JsonObject && actual is JsonObject -> {
                val e = expected.filterValues { it !is JsonNull }
                val a = actual.filterValues { it !is JsonNull }
                assertEquals("keys differ at $path", e.keys, a.keys)
                e.keys.forEach { k ->
                    assertTreesEquivalent(e.getValue(k), a.getValue(k), "$path.$k")
                }
            }
            expected is JsonPrimitive && actual is JsonPrimitive -> {
                val e = expected.doubleOrNull
                val a = actual.doubleOrNull
                if (e != null && a != null) {
                    assertTrue("number differs at $path: $e vs $a", abs(e - a) <= 1e-9 * maxOf(1.0, abs(e)))
                } else {
                    assertEquals("value differs at $path", expected, actual)
                }
            }
            else -> {
                // arrays and mixed cases: compare element-wise via their string forms
                assertEquals("shape differs at $path", expected.toString(), actual.toString())
            }
        }
    }

    companion object {
        /** Resources are on disk under build/resources/test in a Gradle test run; resolve the root once. */
        fun fixturesRoot(): File {
            val url = checkNotNull(SchemaRoundTripTest::class.java.classLoader.getResource("fixtures/SOURCES.md")) {
                "fixtures are not on the test classpath - check src/test/resources/fixtures/ is intact"
            }
            return File(url.toURI()).parentFile
        }
    }
}
