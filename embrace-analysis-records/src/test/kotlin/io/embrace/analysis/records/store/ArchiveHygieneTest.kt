package io.embrace.analysis.records.store

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Culling raw members and serials out of a campaign archive, and what counts as evidence. */
class ArchiveHygieneTest {

    private lateinit var archive: Path
    private lateinit var local: Path
    private lateinit var serials: DeviceSerials.Serials

    @Before
    fun setUp() {
        val fixture = Fixtures.root().toPath().resolve("maxims/campaign-a")
        val run = Files.createTempDirectory("run")
        Files.list(fixture).use { s -> s.forEach { Files.copy(it, run.resolve(it.fileName.toString())) } }
        // The members an archive packed from a whole run directory used to carry.
        Files.writeString(run.resolve("pass1-embverify.log"), "09-04 18:00:01 I EmbVerify: span sdk-init FIXTURE-SERIAL-A\n")
        Files.writeString(run.resolve("pass1-variance.txt"), "rendered report\n")
        Files.writeString(run.resolve("campaign.log"), "18:00 pass 1 on FIXTURE-SERIAL-A done\n")
        val harness = Files.createDirectories(run.resolve("pass1"))
        Files.writeString(harness.resolve("io.embrace.android.benchmark-benchmarkData.json"), "{\"benchmarks\": []}\n")
        Files.writeString(harness.resolve("additionaltestoutput.benchmark.message_StartupBenchmarks.txt"), "harness summary\n")
        archive = Files.createTempDirectory("records").resolve("campaigns").resolve("campaign-a.zip")
        Zips.packTree(run, archive)
        local = Files.createTempDirectory("local").resolve("campaigns")
        serials = DeviceSerials.Serials(mapOf("mid-a" to "FIXTURE-SERIAL-A"))
    }

    @Test
    fun `evidence is the datasets, the harness data, the log and provenance - nothing else`() {
        listOf(
            "pass1.json",
            "pass12-factors.json",
            "pass3-cohorts.json",
            "campaign.log",
            "cell.log",
            "run-metadata.json",
            "cell-state.json",
            "pass1/io.embrace.android.benchmark-benchmarkData.json",
        ).forEach {
            assertTrue(it, ArchiveHygiene.isEvidence(it))
        }
        listOf(
            "pass1-embverify.log",
            "pass1-variance.txt",
            "pass1-gradle.log",
            "pass1/additionaltestoutput.message.txt",
            "notes.md",
            "pass1/deep/benchmarkData.json",
            "traces/x.perfetto-trace",
        ).forEach {
            assertFalse(it, ArchiveHygiene.isEvidence(it))
        }
    }

    @Test
    fun `cull moves raw members to the local root, names the device by key and is idempotent`() {
        val report = ArchiveHygiene.cull(archive, local, serials)

        assertEquals(
            listOf("pass1-embverify.log", "pass1-variance.txt", "pass1/additionaltestoutput.benchmark.message_StartupBenchmarks.txt"),
            report.culled.sorted(),
        )
        assertEquals(listOf("campaign.log", "run-metadata.json"), report.scrubbed.sorted())
        assertTrue(report.unresolved.isEmpty())
        val members = Zips.readAll(archive)
        assertEquals(
            listOf(
                "campaign.log", "pass1-cohorts.json", "pass1-factors.json", "pass1.json",
                "pass1/io.embrace.android.benchmark-benchmarkData.json", "pass2-cohorts.json", "pass2-factors.json", "pass2.json",
                "pass3-cohorts.json", "pass3-factors.json", "pass3.json", "pass4-cohorts.json", "pass4-factors.json", "pass4.json",
                "run-metadata.json",
            ),
            members.keys.sorted(),
        )
        val meta = StartupJson.parseToJsonElement(members.getValue("run-metadata.json").toString(Charsets.UTF_8)).jsonObject
        assertEquals("mid-a", PyJson.strOrNull(meta, "device_key"))
        assertNull(meta["serial"])
        assertEquals("device_key sits where serial was", "device_key", meta.keys.first())
        assertEquals(
            "the log names the device by key",
            "18:00 pass 1 on <mid-a> done\n",
            members.getValue("campaign.log").toString(Charsets.UTF_8),
        )
        val raw = local.resolve("campaign-a")
        assertTrue(Files.exists(raw.resolve("pass1-embverify.log")))
        assertTrue(Files.exists(raw.resolve("pass1-variance.txt")))
        assertTrue(
            "the original provenance is kept locally before it is rewritten",
            Files.readString(raw.resolve("run-metadata.json")).contains("FIXTURE-SERIAL-A"),
        )
        assertTrue("so is the original log", Files.readString(raw.resolve("campaign.log")).contains("FIXTURE-SERIAL-A"))

        val bytes = Files.readAllBytes(archive)
        val second = ArchiveHygiene.cull(archive, local, serials)
        assertFalse(second.changed)
        assertArrayEquals("an archive already in shape is left byte-identical", bytes, Files.readAllBytes(archive))
    }

    @Test
    fun `an unknown serial is still removed and reported as unresolved`() {
        val report = ArchiveHygiene.cull(archive, local, DeviceSerials.Serials.EMPTY)

        assertEquals(listOf("run-metadata.json"), report.unresolved)
        val meta = StartupJson.parseToJsonElement(Zips.readAll(archive).getValue("run-metadata.json").toString(Charsets.UTF_8)).jsonObject
        assertNull(meta["serial"])
        assertTrue("device_key is present but null", "device_key" in meta)
    }

    @Test
    fun `scrub strips serials from device maps in an experiment archive without culling`() {
        val refText = Files.readString(Fixtures.root().toPath().resolve("maxims/reference-set.json"))
        val dir = Files.createTempDirectory("exp")
        Files.writeString(dir.resolve("reference-set-LIVE.json"), refText)
        Files.writeString(dir.resolve("results.txt"), "windows from FIXTURE-SERIAL-A\n")
        Files.writeString(dir.resolve("notes.md"), "nothing here\n")
        val experiment = dir.resolveSibling("x99.zip")
        Zips.packTree(dir, experiment)

        val report = ArchiveHygiene.scrub(experiment, serials)

        assertEquals(listOf("reference-set-LIVE.json", "results.txt"), report.scrubbed.sorted())
        val members = Zips.readAll(experiment)
        assertEquals("nothing is culled from an experiment", 3, members.size)
        assertFalse(members.getValue("reference-set-LIVE.json").toString(Charsets.UTF_8).contains("FIXTURE-SERIAL"))
        assertEquals("windows from <mid-a>\n", members.getValue("results.txt").toString(Charsets.UTF_8))
        assertFalse(ArchiveHygiene.scrub(experiment, serials).changed)
    }
}
