package io.embrace.android.embracesdk.macrobenchmark.app.storage

import java.io.File

/**
 * The fixture a benchmark records: the session parts its app instances left, kept in a directory of its own
 * in the app's data directory, [dataDir], beside the SDK's storage rather than in it. Clearing the SDK's
 * storage leaves it untouched, so it lasts from the fixture's recording through every iteration that starts
 * from it.
 */
internal class FixtureStorage(dataDir: File) {

    private val fixtureDir = File(dataDir, FIXTURE_DIR)

    /**
     * Copies every session part in [sessionsDir] into the fixture.
     */
    fun saveSessionParts(sessionsDir: File) {
        sessionsDir.listFiles().orEmpty().forEach { part -> part.copyRecursively(File(fixtureDir, part.name)) }
    }

    /**
     * Copies the fixture's session parts into [sessionsDir], and returns how many parts it copied.
     */
    fun restoreSessionParts(sessionsDir: File): Int {
        val parts = fixtureDir.listFiles().orEmpty()
        parts.forEach { part -> part.copyRecursively(File(sessionsDir, part.name)) }
        return parts.size
    }

    private companion object {
        const val FIXTURE_DIR = "benchmark_fixture"
    }
}
