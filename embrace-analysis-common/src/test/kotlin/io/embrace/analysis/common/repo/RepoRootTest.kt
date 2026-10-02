package io.embrace.analysis.common.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * [RepoRoot.locate] as a caller sees it: from any subdirectory of a real checkout it climbs to the
 * `.git` root, and outside any checkout it falls back to the working directory rather than throwing.
 */
class RepoRootTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `locate from a subdirectory of this checkout climbs to the git root`() {
        val subdirectory = Path.of("").toAbsolutePath()

        val root = RepoRoot.locate(from = subdirectory)

        assertTrue("$root should contain .git", Files.isDirectory(root.resolve(".git")))
        assertTrue("$root should be an ancestor of $subdirectory", subdirectory.startsWith(root))
    }

    @Test
    fun `locate outside any checkout falls back to the working directory instead of throwing`() {
        val outsideAnyCheckout = tempFolder.newFolder("outside").toPath()

        val root = RepoRoot.locate(from = outsideAnyCheckout)

        assertEquals(Path.of("").toAbsolutePath(), root)
    }
}
