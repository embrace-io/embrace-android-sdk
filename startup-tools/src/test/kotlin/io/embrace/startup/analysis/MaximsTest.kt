package io.embrace.startup.analysis

import io.embrace.startup.cli.MaximsCommand
import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.store.MaximsLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parity gate against the Python `maxims.py` on the closed-form fixture under `fixtures/maxims/`
 * (see its MANIFEST.md): the `score` stdout for both campaigns into one fresh ledger, the ledger after
 * both, `render` on that ledger, and a `--ledger none` rerun.
 */
class MaximsTest {

    private lateinit var fixtures: Path
    private lateinit var tmp: Path
    private lateinit var ledger: Path

    @Before
    fun setUp() {
        fixtures = SchemaRoundTripTest.fixturesRoot().toPath().resolve("maxims")
        tmp = Files.createTempDirectory("maxims")
        ledger = tmp.resolve("ledger.json")
    }

    @Test
    fun `score, ledger and render match the Python goldens`() {
        val ref = fixtures.resolve("reference-set.json")

        val records = tmp.resolve("records")
        val kept = records.resolve("campaigns").resolve("campaign-a.zip")
        val a = MaximsCommand.score(fixtures.resolve("campaign-a"), ref, null, null, null, ledger.toString(), null, NOW_A, records)
        val keptLine = "records: 13 files kept in $kept\n"
        assertTrue(a.endsWith(keptLine))
        assertEquals(golden("score-a.stdout.txt"), a.removeSuffix(keptLine).replace(ledger.toString(), "<ledger>"))
        val unpacked = MaximsCommand.materialize(kept)
        assertEquals(
            Files.list(fixtures.resolve("campaign-a")).use { s -> s.map { it.fileName.toString() }.sorted().toList() },
            Files.list(unpacked).use { s -> s.map { it.fileName.toString() }.sorted().toList() },
        )
        // The archive is a complete campaign: scoring it (run id from the archive name) reproduces the directory's stdout.
        assertEquals(
            golden(
                "score-a.stdout.txt",
            ).replace("<ledger>", "none").replace(Regex("ledger: .*\n"), "ledger: not recorded (--ledger none)\n"),
            MaximsCommand.score(kept, ref, null, null, null, "none", null, NOW_A),
        )
        assertEquals(
            "records: campaign already under $records\n",
            MaximsCommand.keepDatasets(kept, unpacked, records, "campaign-a"),
        )

        val b = MaximsCommand.score(fixtures.resolve("campaign-b"), ref, null, null, null, ledger.toString(), null, NOW_B)
        assertEquals(golden("score-b.stdout.txt"), b.replace(ledger.toString(), "<ledger>"))

        assertEquals(parse(golden("ledger.json")), parse(Files.readString(ledger)))
        assertEquals(golden("ledger.json"), Files.readString(ledger))

        val doc = tmp.resolve("MAXIMS.md")
        assertEquals("wrote $doc\n", MaximsCommand.render(ledger.toString(), doc.toString(), NOW_DOC))
        assertEquals(golden("MAXIMS.md"), Files.readString(doc))
        assertEquals(golden("MAXIMS.md"), MaximsCommand.render(ledger.toString(), "-", NOW_DOC))

        val noLedger = MaximsCommand.score(fixtures.resolve("campaign-b"), ref, null, null, null, "none", null, NOW_B)
        assertEquals(golden("score-b-noledger.stdout.txt"), noLedger)
        assertEquals(golden("ledger.json"), Files.readString(ledger))
    }

    @Test
    fun `overrides replace the provenance's device, version and arm`() {
        val out = MaximsCommand.score(fixtures.resolve("campaign-b"), null, "loaner", "9.3.0-SNAPSHOT", "speed", "none", "rerun", NOW_B)
        assertEquals("maxims: loaner / 9.3.0-SNAPSHOT / speed (run rerun, 60 iterations in 3 passes)", out.lineSequence().first())
    }

    @Test
    fun `a Python-written ledger loads and re-saves without changing its tree`() {
        val copy = tmp.resolve("python-ledger.json")
        Files.copy(fixtures.resolve("ledger.json"), copy)
        val loaded = MaximsLedger.load(copy)
        val resaved = tmp.resolve("resaved.json")
        MaximsLedger.save(loaded, resaved)
        assertEquals(parse(Files.readString(copy)), parse(Files.readString(resaved)))
        assertEquals(Files.readString(copy), Files.readString(resaved))
    }

    @Test
    fun `an empty ledger renders every maxim as untested`() {
        val text = MaximsCommand.render("none", "-", NOW_DOC)
        Maxims.ALL.forEach { m ->
            assertTrue(m.id, text.contains("| ${m.id} | ${m.scope} | untested | no device has tested it |"))
        }
        assertEquals(Maxims.ALL.size, text.split("- Evidence: none yet.").size - 1)
    }

    private fun golden(name: String): String = Files.readString(fixtures.resolve(name))

    private fun parse(text: String) = StartupJson.parseToJsonElement(text)

    private companion object {
        const val NOW_A = "2026-09-04T20:00:00Z"
        const val NOW_B = "2026-09-04T21:00:00Z"
        const val NOW_DOC = "2026-09-04T21:30:00Z"
    }
}
