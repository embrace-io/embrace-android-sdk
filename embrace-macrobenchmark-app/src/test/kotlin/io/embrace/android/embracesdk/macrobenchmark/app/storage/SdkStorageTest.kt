package io.embrace.android.embracesdk.macrobenchmark.app.storage

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class SdkStorageTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val fs get() = fsRule.fileSystem
    private val dataDir: Path = "/data".toPath()
    private lateinit var sdk: SdkStorage

    @Before
    fun setUp() {
        fs.createDirectories(dataDir)
        sdk = SdkStorage(fs, dataDir)
    }

    @Test
    fun `clearing deletes everything the sdk can have written`() {
        writePart("1000_a")
        write(sdk.cacheDir / "payload")
        sdk.writeRemoteConfig("{}")
        write(dataDir / "shared_prefs/io.embrace.prefs.xml")
        write(dataDir / "cache/okhttp")
        write(dataDir / "databases/db")
        write(dataDir / "no_backup/id")

        sdk.clear()

        listOf("files", "cache", "shared_prefs", "databases", "no_backup").forEach { dir ->
            assertFalse("$dir is left", fs.exists(dataDir / dir))
        }
        assertEquals(emptyList<String>(), sdk.sessionPartDirs())
    }

    @Test
    fun `clearing leaves the app's code and directories that are not the sdk's`() {
        write(dataDir / "code_cache/kept")
        write(dataDir / "benchmark_fixture/1000_a/metadata.pb")

        sdk.clear()

        assertTrue(fs.exists(dataDir / "code_cache/kept"))
        assertTrue(fs.exists(dataDir / "benchmark_fixture/1000_a/metadata.pb"))
    }

    @Test
    fun `the session parts are listed by directory name in order`() {
        writePart("2000_b")
        writePart("1000_a")
        assertEquals(listOf("1000_a", "2000_b"), sdk.sessionPartDirs())
    }

    @Test
    fun `session parts and cached payloads are observed`() {
        writePart("1000_a")
        write(sdk.cacheDir / "payload")

        assertTrue(sdk.hasSessionPart("1000_a"))
        assertFalse(sdk.hasSessionPart("2000_b"))
        assertEquals(1, sdk.cachedPayloadCount())
    }

    private fun writePart(name: String) = write(sdk.sessionsDir / name / "metadata.pb")

    private fun write(path: Path) {
        path.parent?.let(fs::createDirectories)
        fs.write(path) { writeUtf8("content") }
    }
}
