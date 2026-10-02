package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import io.embrace.analysis.records.store.ArchiveHygiene
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.EvidenceClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * A records root built from the maxims fixtures: `campaign-a` twice (the second stamped a day later, as a
 * replicate of the same cell) and `campaign-b` once, with the fixture reference set split into the
 * committed form plus a local serial map. Shared by the index, link-check and replicates tests.
 */
object FixtureRecords {

    data class Root(val records: Path, val serials: DeviceSerials.Serials)

    fun build(): Root {
        val fixtures = Fixtures.root().toPath().resolve("maxims")
        val records = Files.createTempDirectory("records")
        val campaigns = Files.createDirectories(records.resolve("campaigns"))
        Zips.packTree(fixtures.resolve("campaign-a"), campaigns.resolve("campaign-a.zip"))
        Zips.packTree(fixtures.resolve("campaign-b"), campaigns.resolve("campaign-b.zip"))
        val replicate = Files.createTempDirectory("campaign-a-again")
        Files.list(fixtures.resolve("campaign-a")).use { s ->
            s.forEach {
                Files.copy(it, replicate.resolve(it.fileName.toString()))
            }
        }
        val meta = StartupJson.parseToJsonElement(Files.readString(replicate.resolve("run-metadata.json"))).jsonObject
        val later = JsonObject(meta + ("started" to JsonPrimitive("2026-09-05T18:00:00")))
        Files.writeString(replicate.resolve("run-metadata.json"), StartupJson.encodeToString(JsonObject.serializer(), later))
        Zips.packTree(replicate, campaigns.resolve("campaign-a-again.zip"))
        val refDoc = StartupJson.parseToJsonElement(Files.readString(fixtures.resolve("reference-set.json"))).jsonObject
        val (committed, serials) = DeviceSerials.split(refDoc)
        // The fixtures' provenance carries serials, as every run directory does; the committed root does not.
        val local = Files.createTempDirectory("local").resolve("campaigns")
        Files.list(campaigns).use { s -> s.toList() }.forEach { ArchiveHygiene.cull(it, local, serials) }
        Files.createDirectories(records.resolve("longitudinal"))
        Files.writeString(records.resolve("longitudinal/reference-set.json"), PyJson.dumpsPretty(committed))
        val maxims = Files.createDirectories(records.resolve("maxims"))
        Files.writeString(
            maxims.resolve("ledger-runs.json"),
            """{"runs": [{"run": "campaign-a"}, {"run": "campaign-a-again"}, """ +
                """{"run": "campaign-b", "device": "mid-b", "sdk": "9.2.0", "arm": "full"}]}""",
        )
        Files.writeString(maxims.resolve("FINDINGS.md"), "# Findings\n\nNothing yet.\n")
        return Root(records, serials)
    }
}

class EvidenceIndexTest {

    private lateinit var root: FixtureRecords.Root

    @Before
    fun setUp() {
        root = FixtureRecords.build()
    }

    @Test
    fun `one entry per archive, cells resolved as scoring resolves them, replicates numbered by start time`() {
        val entries = EvidenceIndex.build(root.records, root.serials)

        assertEquals(listOf("campaign-a", "campaign-a-again", "campaign-b"), entries.map { it.entry.runId })
        val a = entries.first { it.entry.runId == "campaign-a" }
        val again = entries.first { it.entry.runId == "campaign-a-again" }
        val b = entries.first { it.entry.runId == "campaign-b" }
        assertEquals("mid-a", a.entry.deviceKey)
        assertEquals("9.2.0", a.entry.sdkVersion)
        assertEquals("profile", a.entry.arm)
        assertEquals("mid-a / 9.2.0 / profile / coldStartupBaselineProfile / 4x20", a.entry.replicateKey)
        assertEquals(a.entry.replicateKey, again.entry.replicateKey)
        assertEquals(1 to 2, a.replicateIndex to a.replicateOf)
        assertEquals(2 to 2, again.replicateIndex to again.replicateOf)
        assertEquals(
            "the ledger-runs override names campaign-b's cell",
            "mid-b / 9.2.0 / full",
            "${b.entry.deviceKey} / ${b.entry.sdkVersion} / ${b.entry.arm}",
        )
        assertEquals(1 to 1, b.replicateIndex to b.replicateOf)
        assertEquals(EvidenceClass.REPRODUCIBLE, a.entry.evidence.klass)
        assertEquals(80, a.entry.launches)
        assertEquals(4, a.entry.passMedians.size)
        assertTrue(a.entry.median <= a.entry.p90 && a.entry.p90 <= a.entry.p95 && a.entry.p95 <= a.entry.max)
        assertTrue(a.entry.scored)
        assertTrue(a.entry.rawMembers.isEmpty())
    }

    @Test
    fun `write renders a deterministic document that the link check can compare against`() {
        val first = EvidenceIndex.write(root.records, root.serials)
        val text = Files.readString(EvidenceIndex.file(root.records))
        EvidenceIndex.write(root.records, root.serials)

        assertTrue(first, first.startsWith("records: indexed 3 archives in "))
        assertEquals("regenerating changes nothing", text, Files.readString(EvidenceIndex.file(root.records)))
        val doc = StartupJson.parseToJsonElement(text).jsonObject
        assertEquals(3, PyJson.double(doc, "archives")?.toInt())
        val entry = PyJson.arr(doc, "entries")!!.first().jsonObject
        assertEquals("campaigns/campaign-a.zip", PyJson.strOrNull(entry, "archive"))
        assertEquals("reproducible", PyJson.strOrNull(entry, "evidence_class"))
        assertEquals("type7", PyJson.strOrNull(PyJson.obj(entry, "summary"), "quantile"))
        assertFalse("no serial reaches the index", text.contains("FIXTURE-SERIAL"))
    }
}
