package io.embrace.android.embracesdk.macrobenchmark.app.storage

import okio.FileSystem
import okio.Path

/**
 * The fixture a benchmark records: the session parts its app instances left, kept in a directory of its own
 * in the app's data directory, [dataDir], beside the SDK's storage rather than in it. Clearing the SDK's
 * storage leaves it untouched, so it lasts from the fixture's recording through every iteration that starts
 * from it.
 */
internal class FixtureStorage(
    private val fileSystem: FileSystem,
    dataDir: Path,
) {

    private val fixtureDir = dataDir / FIXTURE_DIR

    /**
     * Copies every session part in [sessionsDir] into the fixture.
     */
    fun saveSessionParts(sessionsDir: Path) {
        list(sessionsDir).forEach { part -> copyRecursively(part, fixtureDir / part.name) }
    }

    /**
     * Copies the fixture's session parts into [sessionsDir], and returns how many parts it copied.
     */
    fun restoreSessionParts(sessionsDir: Path): Int {
        val parts = list(fixtureDir)
        parts.forEach { part -> copyRecursively(part, sessionsDir / part.name) }
        return parts.size
    }

    private fun list(dir: Path): List<Path> = fileSystem.listOrNull(dir).orEmpty()

    private fun copyRecursively(src: Path, dst: Path) {
        if (fileSystem.metadata(src).isDirectory) {
            fileSystem.createDirectories(dst)
            fileSystem.list(src).forEach { copyRecursively(it, dst / it.name) }
        } else {
            dst.parent?.let(fileSystem::createDirectories)
            fileSystem.copy(src, dst)
        }
    }

    private companion object {
        const val FIXTURE_DIR = "benchmark_fixture"
    }
}
