package io.embrace.analysis.localrefs

import io.embrace.analysis.common.io.Zips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The pattern table and the two scopes, on a synthetic repository shaped like this one (skills as
 * source, records as evidence). The sweep over the REAL repository lives with the CLI, which is what
 * knows this repository's layout.
 */
class LocalRefsCheckTest {

    private lateinit var repo: Path

    private val roots = LocalRefsCheck.Roots(
        source = listOf(".claude/skills"),
        data = listOf(".claude/skills/_shared/records"),
        skipInSource = setOf("records"),
    )

    @Before
    fun setUp() {
        repo = Files.createTempDirectory("local-refs-repo")
    }

    /** The samples live on the patterns, so this fails the moment one is added without them or drifts. */
    @Test
    fun `every pattern matches its own positive sample and clears its negative`() {
        assertEquals(emptyList<String>(), LocalRefsCheck.selfTest())
    }

    @Test
    fun `evidence may cite a run, the skills may not, and neither may carry a home path`() {
        write(
            "claude/skills/startup-analysis/SKILL.md",
            "Read the report at https://claude.ai/code/artifact/abcd1234 and the run in claude-output/2026-08-11-campaign.",
            "It lives under /Users/alice/work/embrace-android-sdk.",
        )
        write(
            "claude/skills/_shared/records/documents/findings.md",
            "Read the report at https://claude.ai/code/artifact/abcd1234, from claude-output/2026-08-11-campaign.",
            "The trace processor ran from /Users/alice/.cache/embrace-startup-tools.",
        )

        val report = LocalRefsCheck.scan(repo, roots = roots)

        assertEquals(
            "the skill is held to every pattern",
            listOf("claude-artifact-url", "scratch-run-dir", "home-path"),
            report.findings.filter { it.file.contains("SKILL.md") }.map { it.pattern },
        )
        assertEquals(
            "the evidence keeps its citations and loses the home path",
            listOf("home-path"),
            report.findings.filter { it.file.contains("findings.md") }.map { it.pattern },
        )
    }

    @Test
    fun `a local reference inside a records archive is still found`() {
        val loose = Files.createDirectories(repo.resolve("campaign")).resolve("campaign.log")
        Files.writeString(loose, "wrote /Users/alice/work/out.perfetto-trace\n")
        Zips.packTree(loose.parent, path("claude/skills/_shared/records/campaigns/run.zip"))

        val report = LocalRefsCheck.scan(repo, roots = roots)

        val found = report.findings.single { it.file.endsWith("!campaign.log") }
        assertEquals("home-path", found.pattern)
        assertTrue(found.file, found.file.contains("run.zip"))
    }

    @Test
    fun `an inline marker and an allow regex each suppress a hit`() {
        write(
            "claude/skills/x/SKILL.md",
            "serial R58W211D4ZD is the mid tier",
            "serial RZ8RA1SQVAN is the entry tier  <!-- local-refs:allow -->",
        )

        assertEquals(
            "the marked line is skipped, the other is reported",
            listOf("R58W211D4ZD"),
            LocalRefsCheck.scan(repo, roots = roots).findings.map { it.text },
        )
        assertEquals(
            emptyList<String>(),
            LocalRefsCheck.scan(repo, allow = listOf(Regex("R58W211D4ZD")), roots = roots).findings,
        )
    }

    @Test
    fun `a local token is matched case-insensitively and named in the finding`() {
        write("claude/skills/x/SKILL.md", "the host is Analysis-Box-01 today")

        val findings = LocalRefsCheck.scan(repo, localTokens = listOf("analysis-box-01"), roots = roots).findings

        assertEquals(listOf("local:analysis-box-01"), findings.map { it.pattern })
        assertEquals(1, findings.single().line)
    }

    private fun path(rel: String): Path = repo.resolve(rel.replace("claude/", ".claude/"))

    private fun write(rel: String, vararg lines: String) {
        val file = path(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, lines.joinToString("\n") + "\n")
    }
}
