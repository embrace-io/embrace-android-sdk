package io.embrace.analysis.fixtures

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Fixtures] as every other module's tests use it: the root resolves to a real directory holding the
 * set's own provenance file, and [Fixtures.path] resolves relative to that root.
 */
class FixturesTest {

    @Test
    fun `root resolves to a directory containing SOURCES md`() {
        val root = Fixtures.root()

        assertTrue(root.isDirectory)
        assertTrue(root.resolve("SOURCES.md").isFile)
    }

    @Test
    fun `path resolves relative to the fixtures root`() {
        val manifest = Fixtures.path("goldens/MANIFEST.md")

        assertTrue(manifest.isFile)
    }
}
