package io.embrace.analysis.records.store

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import io.embrace.analysis.records.ReferenceSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** The local device-serial map: splitting serials out of a reference set, and putting them back for readers. */
class DeviceSerialsTest {

    private lateinit var refDoc: JsonObject
    private lateinit var local: Path

    @Before
    fun setUp() {
        val text = Files.readString(Fixtures.root().toPath().resolve("maxims/reference-set.json"))
        refDoc = StartupJson.parseToJsonElement(text).jsonObject
        local = Files.createTempDirectory("local")
    }

    @Test
    fun `split removes every serial from the document and keeps them by key`() {
        val (stripped, serials) = DeviceSerials.split(refDoc)

        assertEquals("FIXTURE-SERIAL-A", serials.serialFor("mid-a"))
        assertEquals("mid-a", serials.keyFor("FIXTURE-SERIAL-A"))
        assertFalse("no serial survives in the committed form", PyJson.dumps(stripped).contains("FIXTURE-SERIAL"))
        assertEquals(
            "every other field of every device is untouched",
            PyJson.obj(refDoc, "devices")!!.keys,
            PyJson.obj(stripped, "devices")!!.keys,
        )
        // Splitting the stripped document again is a no-op with an empty map.
        val (again, none) = DeviceSerials.split(stripped)
        assertEquals(stripped, again)
        assertTrue(none.isEmpty())
    }

    @Test
    fun `hydrate fills serials back in from the map, typed and loose`() {
        val (stripped, serials) = DeviceSerials.split(refDoc)
        // The fixture predates the declaration stamp and the per-device cool gate the typed schema requires;
        // the loose form is what the fixture pins, so the typed decode gets both filled in here.
        val gated = JsonObject(
            stripped + ("declared_at" to JsonPrimitive("2026-08-11T00:00:00")) + (
                "devices" to JsonObject(
                    PyJson.obj(stripped, "devices")!!.mapValues { (_, cfg) ->
                        JsonObject(cfg.jsonObject + ("cool_gate_c" to JsonPrimitive(32.0)))
                    },
                )
                ),
        )
        val typed = StartupJson.decodeFromJsonElement(ReferenceSet.serializer(), gated)
        assertNull("the committed form decodes with no serial", typed.devices.getValue("mid-a").serial)

        val hydratedTyped = DeviceSerials.hydrate(typed, serials)
        val hydratedLoose = DeviceSerials.hydrate(stripped, serials)

        assertEquals("FIXTURE-SERIAL-A", hydratedTyped.devices.getValue("mid-a").serial)
        assertEquals("FIXTURE-SERIAL-A", PyJson.strOrNull(PyJson.obj(PyJson.obj(hydratedLoose, "devices"), "mid-a"), "serial"))
        assertEquals("a document that already carries serials is returned as it stands", refDoc, DeviceSerials.hydrate(refDoc, serials))
    }

    @Test
    fun `save and load round-trip, and merge lets the local map win over a legacy document`() {
        val file = local.resolve("data").resolve(DeviceSerials.FILE_NAME)
        DeviceSerials.save(DeviceSerials.Serials(mapOf("mid-a" to "NEW-SERIAL-A", "entry-a" to "SERIAL-E")), file)

        val loaded = DeviceSerials.load(file)
        val merged = DeviceSerials.merge(loaded, refDoc)

        assertEquals(mapOf("entry-a" to "SERIAL-E", "mid-a" to "NEW-SERIAL-A"), loaded.byKey.toSortedMap())
        assertEquals("the local map overrides the document's serial for the same key", "NEW-SERIAL-A", merged.serialFor("mid-a"))
        assertEquals(
            "keys only the document knows are kept",
            "FIXTURE-SERIAL-B",
            merged.byKey.values.first {
                it.endsWith("-B")
            },
        )
        assertTrue(DeviceSerials.load(local.resolve("missing.json")).isEmpty())
    }
}
