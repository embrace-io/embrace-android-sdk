package io.embrace.startup.cli

import io.embrace.startup.core.io.Zips
import io.embrace.startup.core.json.SchemaRoundTripTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** `records pack` and `records rebuild-ledger`, the two housekeeping commands for the records root. */
class RecordsCommandTest {

    private lateinit var fixtures: Path
    private lateinit var records: Path

    @Before
    fun setUp() {
        fixtures = SchemaRoundTripTest.fixturesRoot().toPath().resolve("maxims")
        records = Files.createTempDirectory("records")
    }

    @Test
    fun `pack folds a dropped directory and groups loose summaries by month`() {
        val experiment = Files.createDirectories(records.resolve("experiments").resolve("x99-probe"))
        Files.writeString(experiment.resolve("results.txt"), "verdict\n")
        Files.writeString(Files.createDirectories(experiment.resolve("raw")).resolve("windows.json"), "[]\n")
        val analyses = Files.createDirectories(records.resolve("analyses"))
        Files.writeString(analyses.resolve("startup-analysis-2026-08-11-120000.txt"), "august\n")
        Files.writeString(analyses.resolve("startup-analysis-2026-08-12-090000.txt"), "august too\n")
        Files.writeString(analyses.resolve("startup-analysis-2026-09-01-090000.txt"), "september\n")

        val out = RecordsCommand.pack(records)

        assertTrue(out, out.contains("experiments/x99-probe.zip: 2 files"))
        assertTrue(out, out.contains("analyses/2026-08.zip: +2 files (2 total)"))
        assertTrue(out, out.contains("analyses/2026-09.zip: +1 files (1 total)"))
        assertFalse("the packed directory is removed", Files.exists(experiment))
        assertEquals(
            "the tree's own layout survives inside the archive",
            listOf("raw/windows.json", "results.txt"),
            entriesOf(records.resolve("experiments/x99-probe.zip")),
        )
        assertEquals(emptyList<String>(), looseNames(analyses))

        // Idempotent, and a later summary folds into the month it belongs to rather than replacing it.
        assertEquals("records: nothing to pack\n", RecordsCommand.pack(records))
        Files.writeString(analyses.resolve("startup-analysis-2026-08-20-090000.txt"), "later august\n")
        assertTrue(RecordsCommand.pack(records).contains("analyses/2026-08.zip: +1 files (3 total)"))
        assertEquals(3, entriesOf(records.resolve("analyses/2026-08.zip")).size)
    }

    @Test
    fun `rebuild-ledger scores every listed campaign into a fresh ledger`() {
        Zips.packTree(fixtures.resolve("campaign-a"), records.resolve("campaigns/campaign-a.zip"))
        val maxims = Files.createDirectories(records.resolve("maxims"))
        Files.copy(
            fixtures.resolve("reference-set.json"),
            records.resolve("longitudinal/reference-set.json").also {
                Files.createDirectories(it.parent)
            },
        )
        Files.writeString(maxims.resolve("ledger-runs.json"), """{"runs": [{"run": "campaign-a"}]}""")
        Files.writeString(maxims.resolve("ledger.json"), """{"runs": 99, "cells": {}}""")

        val out = RecordsCommand.rebuildLedger(records)

        assertTrue(out, out.contains("maxims: mid-a / 9.2.0 / profile (run campaign-a, 80 iterations in 4 passes)"))
        assertTrue(out, out.contains("now 1 runs"))
        assertTrue(out, out.endsWith("wrote ${maxims.resolve("MAXIMS.md")}\n"))
        assertTrue("the stale ledger is replaced, not appended to", Files.readString(maxims.resolve("ledger.json")).contains("\"runs\": 1"))
        assertTrue(Files.readString(maxims.resolve("MAXIMS.md")).startsWith("# SDK startup maxims (bench)"))
    }

    @Test
    fun `rebuild-ledger refuses a run whose archive is missing`() {
        val maxims = Files.createDirectories(records.resolve("maxims"))
        Files.writeString(maxims.resolve("ledger-runs.json"), """{"runs": [{"run": "never-collected"}]}""")

        val error = runCatching { RecordsCommand.rebuildLedger(records) }.exceptionOrNull()

        assertTrue("$error", error is IllegalArgumentException)
        assertTrue("$error", error!!.message!!.contains("no campaign archive"))
    }

    private fun entriesOf(archive: Path): List<String> {
        val dir = Zips.unpackToTemp(archive)
        return Files.walk(dir).use { walk ->
            walk.filter { Files.isRegularFile(it) }.map { dir.relativize(it).toString() }.sorted().toList()
        }
    }

    private fun looseNames(dir: Path): List<String> =
        Files.list(dir).use { stream ->
            stream.filter { !it.fileName.toString().endsWith(".zip") }.map { it.fileName.toString() }.sorted().toList()
        }
}
