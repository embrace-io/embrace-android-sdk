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
        fixture.save(sessionsDir)

        fs.deleteRecursively(sessionsDir)
        writePart("2000_b", "metadata")
        writePart("2500_c", "metadata")
        fixture.save(sessionsDir)

        fs.deleteRecursively(sessionsDir)
        assertEquals(3, fixture.restore(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b", "2500_c"), listNames(sessionsDir))
    }

    @Test
    fun `saving from a directory that does not exist saves nothing`() {
        fs.deleteRecursively(sessionsDir)
        fixture.save(sessionsDir)

        assertEquals(0, fixture.restore(sessionsDir))
    }

    @Test
    fun `each directory's entries are kept apart and restored only into it`() {
        val cacheDir = root / "cache"
        writePart("1000_a", "metadata")
        write(cacheDir / "payload", "cached")
        fixture.save(sessionsDir)
        fixture.save(cacheDir)
        fs.deleteRecursively(sessionsDir)
        fs.deleteRecursively(cacheDir)

        assertEquals(1, fixture.restore(cacheDir))
        assertEquals(listOf("payload"), listNames(cacheDir))
        assertEquals("cached", fs.read(cacheDir / "payload") { readUtf8() })
        assertEquals(1, fixture.restore(sessionsDir))
        assertEquals(listOf("1000_a"), listNames(sessionsDir))
    }

    @Test
    fun `restoring copies the saved session parts into the directory intact`() {
        writePart("1000_a", "first part")
        writePart("2000_b", "second part")
        fixture.save(sessionsDir)
        fs.deleteRecursively(sessionsDir)

        assertEquals(2, fixture.restore(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b"), listNames(sessionsDir))
        assertEquals("first part", fs.read(sessionsDir / "1000_a/metadata.pb") { readUtf8() })
    }

    @Test
    fun `restoring is repeatable, as every iteration restores into an emptied directory`() {
        writePart("1000_a", "first part")
        fixture.save(sessionsDir)

        repeat(3) {
            fs.deleteRecursively(sessionsDir)
            assertEquals(1, fixture.restore(sessionsDir))
            assertEquals(listOf("1000_a"), listNames(sessionsDir))
        }
    }

    @Test
    fun `restoring with nothing saved copies nothing`() {
        assertEquals(0, fixture.restore(sessionsDir))
        assertEquals(emptyList<String>(), listNames(sessionsDir))
    }

    private fun listNames(dir: Path): List<String> = fs.listOrNull(dir).orEmpty().map(Path::name).sorted()

    private fun writePart(name: String, metadata: String) = write(sessionsDir / name / "metadata.pb", metadata)

    private fun write(path: Path, text: String) {
        path.parent?.let(fs::createDirectories)
        fs.write(path) { writeUtf8(text) }
    }
}
