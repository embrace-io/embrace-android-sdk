package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/** The provenance chain, checked both ways. */
class LinkCheckTest {

    private lateinit var root: FixtureRecords.Root

    @Before
    fun setUp() {
        root = FixtureRecords.build()
    }

    @Test
    fun `a root whose archives are scored and indexed passes clean`() {
        EvidenceIndex.write(root.records, root.serials)

        assertEquals(emptyList<LinkCheck.Problem>(), LinkCheck.run(root.records, root.serials))
    }

    @Test
    fun `orphans, dangling runs and citations, raw members and a stale index are each named`() {
        EvidenceIndex.write(root.records, root.serials)
        val campaigns = root.records.resolve("campaigns")
        // An archive nobody scored or cited, an archive that still carries a raw member and a serial, a
        // ledger run without its archive, a finding citing an archive that does not exist, and an
        // experiment archive nobody cited.
        Files.copy(campaigns.resolve("campaign-b.zip"), campaigns.resolve("campaign-c.zip"))
        val raw = Files.createTempDirectory("raw")
        Files.copy(Fixtures.root().toPath().resolve("maxims/campaign-b/pass1.json"), raw.resolve("pass1.json"))
        Files.writeString(raw.resolve("pass1-embverify.log"), "logcat")
        Files.writeString(raw.resolve("run-metadata.json"), """{"serial": "X", "method": "coldStartup"}""")
        Zips.packTree(raw, campaigns.resolve("campaign-d.zip"))
        val maxims = root.records.resolve("maxims")
        Files.writeString(
            maxims.resolve("ledger-runs.json"),
            """{"runs": [{"run": "campaign-a"}, {"run": "campaign-a-again"}, {"run": "campaign-b"}, """ +
                """{"run": "campaign-d"}, {"run": "never-collected"}]}""",
        )
        Files.writeString(maxims.resolve("FINDINGS.md"), "See `campaigns/campaign-z.zip` and `experiments/x99-probe.zip`.\n")
        Files.createDirectories(root.records.resolve("experiments"))
        Zips.packTree(raw, root.records.resolve("experiments/x98-uncited.zip"))

        val problems = LinkCheck.run(root.records, root.serials)

        val byKind = problems.groupBy { it.kind }.mapValues { (_, v) -> v.map { it.subject }.sorted() }
        assertEquals(listOf("campaigns/campaign-c.zip", "experiments/x98-uncited.zip"), byKind["orphan"])
        assertEquals(
            listOf("FINDINGS.md: `campaign-z`", "FINDINGS.md: `x99-probe`", "ledger-runs.json: never-collected"),
            byKind["dangling"],
        )
        assertEquals(listOf("campaigns/campaign-d.zip!pass1-embverify.log"), byKind["raw-member"])
        assertEquals(listOf("campaigns/campaign-d.zip!run-metadata.json"), byKind["serial"])
        assertEquals(listOf("campaigns/index.json"), byKind["index-stale"])
        assertEquals(
            "every problem kind is one of the documented ones",
            setOf("orphan", "dangling", "raw-member", "serial", "index-stale"),
            byKind.keys,
        )
    }

    @Test
    fun `citations are backticked archive paths and run ids, never prose`() {
        val cited = LinkCheck.citations(
            "The `2026-09-03-first-session-fix-arm-a-9.2.0` run and `experiments/2026-08-16-x25-artifacts.zip`; " +
                "`records/campaigns/a01-mini-2026-08-12.zip` too. Not `emb-sdk-start`, not `pass1.json`, not `tools/startup`.",
        )
        assertEquals(
            setOf("2026-09-03-first-session-fix-arm-a-9.2.0", "2026-08-16-x25-artifacts", "a01-mini-2026-08-12"),
            cited,
        )
        assertTrue(LinkCheck.citations("no backticks here").isEmpty())
    }
}
