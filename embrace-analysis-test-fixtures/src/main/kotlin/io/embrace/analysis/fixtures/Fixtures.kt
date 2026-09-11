package io.embrace.analysis.fixtures

import java.io.File

/**
 * Where the frozen fixtures are. They are a plain directory in this module (`fixtures/`), not packaged
 * resources: a consuming module receives another module's resources as a JAR, and the goldens have to
 * be real files - the trace goldens are an archive that is unpacked, the stores are opened by path.
 *
 * Every module's test task is handed the directory as the `analysis.fixturesDir` system property by the
 * `embrace-analysis-conventions` Gradle plugin, so this resolves the same way from any module and any
 * working directory Gradle uses. A runner that did not set it (an IDE running one test) falls back to
 * the fixtures module as a sibling of the working directory, which is where Gradle runs a module's tests.
 */
object Fixtures {

    /** The `fixtures/` directory. */
    fun root(): File {
        System.getProperty(PROPERTY)?.let { configured ->
            val dir = File(configured)
            check(dir.isDirectory) { "$PROPERTY points at $configured, which is not a directory" }
            return dir
        }
        val sibling = File("../embrace-analysis-test-fixtures/fixtures").absoluteFile.normalize()
        check(sibling.isDirectory) {
            "fixtures not found: run through Gradle, or pass -D$PROPERTY=<path to embrace-analysis-test-fixtures/fixtures>"
        }
        return sibling
    }

    /** [root] resolved to a sub-path, for readability at the call site. */
    fun path(rel: String): File = root().resolve(rel)

    private const val PROPERTY = "analysis.fixturesDir"
}
