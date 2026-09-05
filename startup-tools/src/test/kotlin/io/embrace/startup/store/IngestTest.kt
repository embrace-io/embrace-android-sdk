package io.embrace.startup.store

import io.embrace.startup.core.json.DeviceProfile
import io.embrace.startup.core.json.ReferenceSet
import io.embrace.startup.core.json.RunShape
import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.json.StoreRecord
import io.embrace.startup.core.stats.Derive
import io.embrace.startup.perfetto.TraceGoldens
import io.embrace.startup.perfetto.TraceHealth
import io.embrace.startup.perfetto.TraceReads
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Ingest's record construction and every guard, driven by the frozen per-trace measurements of the
 * Pixel 3 fixture run (health verdicts, windows and signals exactly as the Python read them) so no
 * Perfetto binary is needed. The guards are exercised by varying the reference set's declared shape,
 * device and profile around that fixed run.
 */
class IngestTest {

    private val fixtures: Path = SchemaRoundTripTest.fixturesRoot().toPath()
    private val realRef: ReferenceSet =
        StartupJson.decodeFromString(ReferenceSet.serializer(), Files.readString(fixtures.resolve("longitudinal/reference-set.json")))

    private fun midBMeasurements(): List<Ingest.Measurement> {
        val goldens = TraceGoldens.all().filter { it.device == "mid-b" }
        assumeTrue(goldens.isNotEmpty())
        var signalsTaken = false
        return goldens.sortedBy { it.traceFileName }.map { g ->
            val parsed = g.parsed()
            val health = TraceHealth.evaluate(g.traceFileName, TraceHealth.parseRows(g.csv("health")))
            val window = TraceReads.parseWindow(g.csv("window_emb_sdk_start"))
            val signals = if (!signalsTaken && health.verdict == TraceHealth.Verdict.OK) {
                signalsTaken = true
                TraceReads.parseSignals(g.csv("signals"))
            } else {
                null
            }
            assertEquals(parsed.getValue("window_emb_sdk_start_ms").jsonPrimitive.content.toDouble(), window!!, 0.0)
            Ingest.Measurement(Path.of(g.traceFileName), health, window, signals)
        }
    }

    private fun refFor(instrument: String?, shape: RunShape) =
        realRef.copy(recipe = realRef.recipe.copy(instrument = instrument, runShape = shape))

    private fun provenance(profile: DeviceProfile = realRef.devices.getValue("mid-b").profile): Pair<JsonObject, String> {
        val prov = JsonObject(
            mapOf(
                "serial" to JsonPrimitive(realRef.devices.getValue("mid-b").serial),
                "device_profile" to StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile).jsonObject,
                "build_type" to JsonPrimitive("benchmark"),
                "sdk_version" to JsonPrimitive("9.2.0"),
                "started" to JsonPrimitive("2026-09-02T07:33:00"),
                "apk_sha256" to JsonPrimitive("abc123"),
                "cell" to JsonObject(mapOf("levels" to JsonObject(mapOf("compile" to JsonPrimitive("profile"))))),
            ),
        )
        return prov to "run-metadata.json"
    }

    @Test
    fun `a clean one-pass run of 20 builds a complete, baseline-eligible record from the fixture measurements`() {
        val m = midBMeasurements()
        val out = Ingest.build(
            Path.of("/runs/9.2.0__mid-b"),
            refFor("emb-sdk-start", RunShape(1, 20)),
            provenance(),
            m,
            Path.of("/tp"),
            Ingest.Options(),
            "2026-09-02T08:00:00",
        )
        assertEquals(emptyList<String>(), out.problems)
        val rec = assertNotNull(out.record).let { out.record!! }
        assertEquals("9.2.0__mid-b", rec.runId)
        assertEquals("mid-b", rec.deviceKey)
        assertEquals("9.2.0", rec.sdkVersion)
        assertTrue(rec.baselineEligible)
        assertEquals("emb-sdk-start", rec.recipe.instrument)
        assertEquals("benchmark", rec.recipe.buildType)
        assertEquals("profile", rec.recipe.compileState)
        assertEquals("v57.2", rec.recipe.traceProcessorVersion)
        assertEquals(JsonObject(mapOf("compile" to JsonPrimitive("profile"))), rec.conditions)
        assertEquals(20, rec.windowsMs.size)
        val raw = m.map { it.windowMs!! }
        assertEquals(raw.map { Ingest.roundWindow(it) }, rec.windowsMs)
        assertEquals(Derive.of(raw), rec.derived)
        assertEquals(20, rec.traceHealth!!.traces)
        assertEquals(0, rec.traceHealth!!.bufferLoss)
        assertEquals(0, rec.traceHealth!!.parseErrors)
        assertTrue(rec.traceHealth!!.signalsFromCleanTrace)
        assertTrue("emb-sdk-start" in rec.signalsPresent && rec.signalsPresent.size > 50)
        assertEquals("run-metadata.json", rec.sourceSkill)
        assertEquals("", rec.notes)
        // Round-trips through the store schema exactly.
        val line = StartupJson.encodeToString(StoreRecord.serializer(), rec)
        assertEquals(rec, StartupJson.decodeFromString(StoreRecord.serializer(), line))
        assertTrue(Ingest.summaryLine(rec).startsWith("\n9.2.0__mid-b -> device_key=mid-b n=20 median="))
    }

    @Test
    fun `shape guards - truncated against 10x20, over-long against 1x10, and the missing-instrument refusal`() {
        val m = midBMeasurements()
        val truncated = Ingest.build(Path.of("/r"), refFor("emb-sdk-start", RunShape(10, 20)), provenance(), m, null, Ingest.Options(), "t")
        assertTrue(truncated.problems.single().startsWith("truncated run: 20 windows against a declared shape of 10x20=200"))
        assertNotNull(truncated.record) // still built, so --force can store it with the reason stamped in
        assertTrue(truncated.record!!.notes.startsWith("FORCED ingest despite: truncated run"))

        val overLong = Ingest.build(Path.of("/r"), refFor("emb-sdk-start", RunShape(1, 10)), provenance(), m, null, Ingest.Options(), "t")
        assertTrue(overLong.problems.single().startsWith("over-long run: 20 windows against a declared shape of 1x10=10"))

        val noInstrument = Ingest.build(Path.of("/r"), refFor(null, RunShape(1, 20)), provenance(), m, null, Ingest.Options(), "t")
        assertNull(noInstrument.record)
        assertTrue(noInstrument.refusedOutright!!.startsWith("REFUSED: reference set has no instrument."))
    }

    @Test
    fun `device guards - unknown key, unmapped serial, profile drift, recipe mismatch, and not-baseline`() {
        val m = midBMeasurements()
        val ref = refFor("emb-sdk-start", RunShape(1, 20))
        val unknown = Ingest.build(Path.of("/r"), ref, provenance(), m, null, Ingest.Options(deviceKey = "phantom"), "t")
        assertTrue(
            unknown.problems.single().startsWith(
                "device_key 'phantom' is not in the reference set (known: ['entry-a', 'flagship-a', 'mid-a', 'mid-b'])",
            ),
        )

        val noSerial = Ingest.build(Path.of("/r"), ref, null, m, null, Ingest.Options(), "t")
        assertTrue(noSerial.problems.any { it.startsWith("cannot map this run to a device_key") })
        assertEquals("unknown", noSerial.record!!.deviceKey)
        assertEquals("unknown", noSerial.record!!.sourceSkill)

        val upgraded = realRef.devices.getValue("mid-b").profile.copy(apiLevel = 32, release = "12L")
        val drift = Ingest.build(Path.of("/r"), ref, provenance(upgraded), m, null, Ingest.Options(), "t")
        assertEquals(
            "device profile drift vs reference set: {'api_level': (31, 32), 'release': ('12', '12L')} - an upgraded or " +
                "replaced device must become a NEW device_key",
            drift.problems.single(),
        )

        val mismatch = Ingest.build(
            Path.of("/r"),
            ref.copy(recipe = ref.recipe.copy(compileState = "none")),
            provenance(),
            m,
            null,
            Ingest.Options(),
            "t",
        )
        assertEquals(
            "recipe mismatch on compile_state: frozen='none' run='profile' - this is a different comparable series",
            mismatch.problems.single(),
        )

        val adhoc = Ingest.build(Path.of("/r"), ref, provenance(), m, null, Ingest.Options(notBaseline = true), "t")
        assertTrue(!adhoc.record!!.baselineEligible)
    }

    @Test
    fun `published-version rule and window rounding follow the Python`() {
        assertTrue(Ingest.isPublished("9.2.0"))
        listOf("9.3.0-SNAPSHOT", "local", "9.2.0+dirty", "9.2.0+3", "", null).forEach {
            assertTrue("$it", !Ingest.isPublished(it))
        }
        assertEquals(26.163, Ingest.roundWindow(26.163336), 0.0)
        assertEquals(0.003, Ingest.roundWindow(0.0025), 0.0) // 0.0025 sits just ABOVE the tie in binary, as in Python
        assertEquals(0.062, Ingest.roundWindow(0.0625), 0.0) // an exact tie: half-even, as in Python
        assertEquals("emb-modules-init", Ingest.canaryFor("composed"))
        assertEquals("emb-sdk-start", Ingest.canaryFor("emb-sdk-start"))
    }
}
