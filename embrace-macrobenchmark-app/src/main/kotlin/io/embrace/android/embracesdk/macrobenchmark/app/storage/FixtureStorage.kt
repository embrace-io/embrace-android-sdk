package io.embrace.android.embracesdk.macrobenchmark.app.storage

import okio.FileSystem
import okio.Path

/**
 * The fixture a benchmark records: what its app instances left in the SDK's directories, kept in a directory of
 * its own in the app's data directory, [dataDir], beside the SDK's storage rather than in it. Clearing the SDK's
 * storage leaves it untouched, so it lasts from the fixture's recording through every iteration that starts
 * from it. Each SDK directory is kept apart, under its own name.
 */
internal class FixtureStorage(
    private val fileSystem: FileSystem,
    dataDir: Path,
) {

    private val fixtureDir = dataDir / FIXTURE_DIR

    /**
     * Copies everything in [sdkDir] into the fixture.
     */
    fun save(sdkDir: Path) {
        list(sdkDir).forEach { entry -> copyRecursively(entry, keptDir(sdkDir) / entry.name) }
    }

    /**
     * Copies what the fixture keeps for [sdkDir] back into it, and returns how many entries it copied.
     */
    fun restore(sdkDir: Path): Int {
        val entries = list(keptDir(sdkDir))
        entries.forEach { entry -> copyRecursively(entry, sdkDir / entry.name) }
        return entries.size
    }

    private fun keptDir(sdkDir: Path): Path = fixtureDir / sdkDir.name

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
