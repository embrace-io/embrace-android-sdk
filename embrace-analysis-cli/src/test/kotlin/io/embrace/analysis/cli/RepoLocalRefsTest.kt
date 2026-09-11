package io.embrace.analysis.cli

import io.embrace.analysis.fixtures.TraceGoldens
import io.embrace.analysis.localrefs.LocalRefsCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sweep over THIS repository, with this repository's roots - so it runs whether or not anyone
 * remembers to, and a machine-specific reference fails the suite the same way a broken golden does.
 * Deliberately no local token list: those name one machine's devices, and this has to mean the same
 * thing on every machine. See `.claude/skills/_shared/no-local-references.md` for what to do when it
 * fails.
 */
class RepoLocalRefsTest {

    @Test
    fun `the repo describes the work rather than the machine it ran on`() {
        val findings = LocalRefsCheck.scan(TraceGoldens.repoRoot(), roots = RepoLocalRefs.ROOTS).findings

        assertEquals(findings.joinToString("\n"), emptyList<String>(), findings.map { it.toString() })
    }

    @Test
    fun `the roots name the skills, the wrapper and every module's main sources as source, and the records and fixtures as data`() {
        val roots = RepoLocalRefs.ROOTS

        assertTrue(roots.source.contains(".claude/skills"))
        assertTrue(roots.source.contains("tools"))
        assertTrue(roots.source.contains("embrace-analysis-common/src/main"))
        assertTrue(roots.source.contains("embrace-analysis-cli/README.md"))
        assertTrue(roots.source.contains("embrace-analysis-test-fixtures/src/main/kotlin"))
        assertEquals(listOf(".claude/skills/_shared/records", "embrace-analysis-test-fixtures/fixtures"), roots.data)
        assertEquals(setOf("records"), roots.skipInSource)
    }
}
