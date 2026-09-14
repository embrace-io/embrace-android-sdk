package io.embrace.analysis.localrefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The [LocalRefsCheck.Roots] parameter that lets [LocalRefsCheck.scan] sweep any tree, plus the
 * personal-token-file plumbing ([LocalRefsCheck.loadLocalTokens], [LocalRefsCheck.localTokenFile]). See
 * [LocalRefsCheckTest] for the pattern table and the two scopes; the sweep over this repository itself
 * lives with the CLI, which owns this repository's roots.
 */
class LocalRefsCheckRootsTest {

    private lateinit var repo: Path

    @Before
    fun setUp() {
        repo = Files.createTempDirectory("local-refs-roots-repo")
    }

    @Test
    fun `custom Roots scope every pattern to source, only the everywhere patterns to data, and scan nothing outside either`() {
        val cited = "the trace lives at /Users/alice/work/embrace-android-sdk and results in claude-output/2026-01-02-run"
        write("docs/notes.md", cited)
        write("evidence/log.md", cited)
        write("neither/other.md", cited)

        val report = LocalRefsCheck.scan(
            repo,
            roots = LocalRefsCheck.Roots(source = listOf("docs"), data = listOf("evidence")),
        )

        assertEquals(
            "docs is held to every pattern, source and everywhere alike",
            listOf("home-path", "scratch-run-dir"),
            report.findings.filter { it.file.contains("notes.md") }.map { it.pattern },
        )
        assertEquals(
            "evidence keeps only the everywhere-scoped home-path",
            listOf("home-path"),
            report.findings.filter { it.file.contains("log.md") }.map { it.pattern },
        )
        assertTrue(
            "a directory named in neither root is not scanned at all",
            report.findings.none { it.file.contains("other.md") },
        )
    }

    @Test
    fun `skipInSource excludes a named directory from the source walk even when the source root is the repo itself`() {
        write("evidence/note.md", "the trace lives at /Users/alice/work/embrace-android-sdk")

        val report = LocalRefsCheck.scan(
            repo,
            roots = LocalRefsCheck.Roots(source = listOf("."), data = emptyList(), skipInSource = setOf("evidence")),
        )

        assertEquals(emptyList<String>(), report.findings.map { it.toString() })
    }

    @Test
    fun `loadLocalTokens reads one token per line, stripping comments and blank lines`() {
        Files.writeString(
            repo.resolve(".local-refs-tokens"),
            "analysis-box-01\n# a comment line\n\nanalysis-box-02 # inline comment\n   \n",
        )

        assertEquals(listOf("analysis-box-01", "analysis-box-02"), LocalRefsCheck.loadLocalTokens(repo))
    }

    @Test
    fun `localTokenFile finds the repo local-refs-tokens file, and is null when neither it nor the env var exists`() {
        assertNull(LocalRefsCheck.localTokenFile(repo))

        val tokenFile = repo.resolve(".local-refs-tokens")
        Files.writeString(tokenFile, "analysis-box-01\n")

        assertEquals(tokenFile, LocalRefsCheck.localTokenFile(repo))
    }

    private fun write(rel: String, vararg lines: String) {
        val file = repo.resolve(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, lines.joinToString("\n") + "\n")
    }
}
