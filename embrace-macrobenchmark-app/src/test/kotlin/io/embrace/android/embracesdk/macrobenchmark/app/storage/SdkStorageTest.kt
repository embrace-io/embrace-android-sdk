package io.embrace.android.embracesdk.macrobenchmark.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SdkStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dataDir: File
    private lateinit var sdk: SdkStorage

    @Before
    fun setUp() {
        dataDir = tmp.newFolder("data")
        sdk = SdkStorage(dataDir)
    }

    @Test
    fun `clearing deletes everything the sdk can have written`() {
        writePart("1000_a")
        sdk.cacheDir.apply { mkdirs() }.resolve("payload").writeText("cached")
        sdk.writeRemoteConfig("{}")
        write("shared_prefs/io.embrace.prefs.xml")
        write("cache/okhttp")
        write("databases/db")
        write("no_backup/id")

        sdk.clear()

        listOf("files", "cache", "shared_prefs", "databases", "no_backup").forEach { dir ->
            assertFalse("$dir is left", File(dataDir, dir).exists())
        }
        assertEquals(emptyList<String>(), sdk.sessionPartDirs())
    }

    @Test
    fun `clearing leaves the app's code and directories that are not the sdk's`() {
        write("code_cache/kept")
        write("benchmark_fixture/1000_a/metadata.pb")

        sdk.clear()

        assertTrue(File(dataDir, "code_cache/kept").exists())
        assertTrue(File(dataDir, "benchmark_fixture/1000_a/metadata.pb").exists())
    }

    @Test
    fun `the session parts are listed by directory name in order`() {
        writePart("2000_b")
        writePart("1000_a")
        assertEquals(listOf("1000_a", "2000_b"), sdk.sessionPartDirs())
    }

    private fun writePart(name: String) {
        File(sdk.sessionsDir, name).apply { mkdirs() }.resolve("metadata.pb").writeText("metadata")
    }

    private fun write(path: String) {
        File(dataDir, path).apply { parentFile?.mkdirs() }.writeText("content")
    }
}
