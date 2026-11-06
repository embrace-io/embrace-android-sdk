package io.embrace.android.embracesdk.macrobenchmark.app.storage

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class FixtureStorageTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val fs get() = fsRule.fileSystem
    private val root = "/data".toPath()
    private val sessionsDir: Path = root / "sessions"
    private lateinit var fixture: FixtureStorage

    @Before
    fun setUp() {
        fs.createDirectories(sessionsDir)
        fixture = FixtureStorage(fs, root / "app")
    }

    @Test
    fun `saving adds every session part in the directory to the fixture`() {
        writePart("1000_a", "metadata")
        fixture.saveSessionParts(sessionsDir)

        fs.deleteRecursively(sessionsDir)
        writePart("2000_b", "metadata")
        writePart("2500_c", "metadata")
        fixture.saveSessionParts(sessionsDir)

        fs.deleteRecursively(sessionsDir)
        assertEquals(3, fixture.restoreSessionParts(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b", "2500_c"), listNames())
    }

    @Test
    fun `saving from a directory that does not exist saves nothing`() {
        fixture.saveSessionParts(root / "absent")

        assertEquals(0, fixture.restoreSessionParts(sessionsDir))
    }

    @Test
    fun `restoring copies the saved session parts into the directory intact`() {
        writePart("1000_a", "first part")
        writePart("2000_b", "second part")
        fixture.saveSessionParts(sessionsDir)
        fs.deleteRecursively(sessionsDir)

        assertEquals(2, fixture.restoreSessionParts(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b"), listNames())
        assertEquals("first part", fs.read(sessionsDir / "1000_a/metadata.pb") { readUtf8() })
    }

    @Test
    fun `restoring is repeatable, as every iteration restores into an emptied directory`() {
        writePart("1000_a", "first part")
        fixture.saveSessionParts(sessionsDir)

        repeat(3) {
            fs.deleteRecursively(sessionsDir)
            assertEquals(1, fixture.restoreSessionParts(sessionsDir))
            assertEquals(listOf("1000_a"), listNames())
        }
    }

    @Test
    fun `restoring with nothing saved copies nothing`() {
        assertEquals(0, fixture.restoreSessionParts(sessionsDir))
        assertEquals(emptyList<String>(), listNames())
    }

    private fun listNames(): List<String> = fs.listOrNull(sessionsDir).orEmpty().map(Path::name).sorted()

    private fun writePart(name: String, metadata: String) {
        val dir = sessionsDir / name
        fs.createDirectories(dir)
        fs.write(dir / "metadata.pb") { writeUtf8(metadata) }
    }
}
