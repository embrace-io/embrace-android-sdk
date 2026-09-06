package io.embrace.startup.analysis

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.store.MaximsLedger
import io.embrace.startup.store.MaximsRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parity gate against the frozen golden on the closed-form fixture under `fixtures/maxims/`
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
    fun `score, ledger and render match the goldens`() {
        val ref = fixtures.resolve("reference-set.json")

        val records = tmp.resolve("records")
        val kept = records.resolve("campaigns").resolve("campaign-a.zip")
        val a = MaximsRunner.score(fixtures.resolve("campaign-a"), ref, null, null, null, ledger.toString(), null, NOW_A, records)
        val keptLine = "records: 13 files kept in $kept\n"
        assertTrue(a.endsWith(keptLine))
        assertEquals(golden("score-a.stdout.txt"), a.removeSuffix(keptLine).replace(ledger.toString(), "<ledger>"))
        val unpacked = MaximsRunner.materialize(kept)
        assertEquals(
            Files.list(fixtures.resolve("campaign-a")).use { s -> s.map { it.fileName.toString() }.sorted().toList() },
            Files.list(unpacked).use { s -> s.map { it.fileName.toString() }.sorted().toList() },
        )
        // The archive is a complete campaign: scoring it (run id from the archive name) reproduces the directory's stdout.
        assertEquals(
            golden(
                "score-a.stdout.txt",
            ).replace("<ledger>", "none").replace(Regex("ledger: .*\n"), "ledger: not recorded (--ledger none)\n"),
            MaximsRunner.score(kept, ref, null, null, null, "none", null, NOW_A),
        )
        assertEquals(
            "records: campaign already under $records\n",
            MaximsRunner.keepDatasets(kept, unpacked, records, "campaign-a"),
        )

        val b = MaximsRunner.score(fixtures.resolve("campaign-b"), ref, null, null, null, ledger.toString(), null, NOW_B)
        assertEquals(golden("score-b.stdout.txt"), b.replace(ledger.toString(), "<ledger>"))

        assertEquals(parse(golden("ledger.json")), parse(Files.readString(ledger)))
        assertEquals(golden("ledger.json"), Files.readString(ledger))

        val doc = tmp.resolve("MAXIMS.md")
        assertEquals("wrote $doc\n", MaximsRunner.render(ledger.toString(), doc.toString(), NOW_DOC))
        assertEquals(golden("MAXIMS.md"), Files.readString(doc))
        assertEquals(golden("MAXIMS.md"), MaximsRunner.render(ledger.toString(), "-", NOW_DOC))

        val noLedger = MaximsRunner.score(fixtures.resolve("campaign-b"), ref, null, null, null, "none", null, NOW_B)
        assertEquals(golden("score-b-noledger.stdout.txt"), noLedger)
        assertEquals(golden("ledger.json"), Files.readString(ledger))
    }

    @Test
    fun `overrides replace the provenance's device, version and arm`() {
        val out = MaximsRunner.score(fixtures.resolve("campaign-b"), null, "loaner", "9.3.0-SNAPSHOT", "speed", "none", "rerun", NOW_B)
        assertEquals("maxims: loaner / 9.3.0-SNAPSHOT / speed (run rerun, 60 iterations in 3 passes)", out.lineSequence().first())
    }

    /**
     * Both arms below are single-population by design, so a verdict of "thin" or "contradicted" would
     * blame the SDK for the harness's own setup. The rules make them n/a; these are the regression tests
     * for that, built by varying the fixture rather than by adding a second campaign to it.
     */
    @Test
    fun `an arm that clears app data before every launch cannot test the config fast path`() {
        val campaign = copyCampaign("campaign-a")
        val meta = campaign.resolve("run-metadata.json")
        Files.writeString(
            meta,
            Files.readString(meta).replace("\"coldStartupBaselineProfile\"", "\"coldStartupBaselineProfileNewUserSession\""),
        )

        val out = MaximsRunner.score(campaign, null, "mid-a", "9.2.0", "new-user-session", "none", null, NOW_A)

        val line = verdictLine(out, "config-fast-path")
        assertTrue(line, line.startsWith("n/a"))
        assertTrue(line, line.contains("coldStartupBaselineProfileNewUserSession clears app data before every launch"))

        // The same campaign under its own method still grades: the rule is scoped to the arm, not the check.
        val plain = MaximsRunner.score(campaign.parent.resolve("campaign-a-plain"), null, null, null, null, "none", null, NOW_A)
        assertTrue(plain, verdictLine(plain, "config-fast-path").startsWith("confirmed"))
    }

    @Test
    fun `a single-cohort arm cannot test restore against create`() {
        val campaign = copyCampaign("campaign-a")
        Files.list(campaign).use { it.toList() }
            .filter { it.fileName.toString().endsWith("-cohorts.json") }
            .forEach { Files.writeString(it, Files.readString(it).replace("\"cohort\": \"restored\"", "\"cohort\": \"created\"")) }

        val out = MaximsRunner.score(campaign, null, "mid-a", "9.2.0", "expired-user-session", "none", null, NOW_A)

        val line = verdictLine(out, "restore-vs-create")
        assertTrue(line, line.startsWith("n/a"))
        assertTrue(line, line.contains("single-cohort arm (80 created, 0 restored)"))
        assertTrue(line, line.endsWith("compare against the matching cell of the other arm"))
    }

    @Test
    fun `a frozen golden ledger loads and re-saves without changing its tree`() {
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
        val text = MaximsRunner.render("none", "-", NOW_DOC)
        Maxims.ALL.forEach { m ->
            assertTrue(m.id, text.contains("| ${m.id} | ${m.scope} | untested | no device has tested it |"))
        }
        assertEquals(Maxims.ALL.size, text.split("- Evidence: none yet.").size - 1)
    }

    /** A writable copy of a fixture campaign, plus an untouched `<name>-plain` beside it to compare against. */
    private fun copyCampaign(name: String): Path {
        val src = fixtures.resolve(name)
        val files = Files.list(src).use { it.toList() }
        listOf(tmp.resolve("$name-plain"), tmp.resolve(name)).forEach { dest ->
            Files.createDirectories(dest)
            files.forEach { Files.copy(it, dest.resolve(it.fileName.toString())) }
        }
        return tmp.resolve(name)
    }

    /** The `score` line for one maxim, trimmed and with the padding collapsed. */
    private fun verdictLine(out: String, maximId: String): String =
        out.lineSequence().first { it.contains(maximId) }.trim().replace(Regex(" +"), " ").replace("$maximId ", "")

    private fun golden(name: String): String = Files.readString(fixtures.resolve(name))

    private fun parse(text: String) = StartupJson.parseToJsonElement(text)

    private companion object {
        const val NOW_A = "2026-09-04T20:00:00Z"
        const val NOW_B = "2026-09-04T21:00:00Z"
        const val NOW_DOC = "2026-09-04T21:30:00Z"
    }
}
