package io.embrace.android.embracesdk.macrobenchmark.app.storage

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class FixtureStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var fixture: FixtureStorage

    @Before
    fun setUp() {
        sessionsDir = tmp.newFolder("sessions")
        fixture = FixtureStorage(tmp.newFolder("data"))
    }

    @Test
    fun `saving adds every session part in the directory to the fixture`() {
        writePart("1000_a", "metadata")
        fixture.saveSessionParts(sessionsDir)

        sessionsDir.deleteRecursively()
        writePart("2000_b", "metadata")
        writePart("2500_c", "metadata")
        fixture.saveSessionParts(sessionsDir)

        sessionsDir.deleteRecursively()
        assertEquals(3, fixture.restoreSessionParts(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b", "2500_c"), sessionsDir.list().orEmpty().sorted())
    }

    @Test
    fun `saving from a directory that does not exist saves nothing`() {
        fixture.saveSessionParts(File(tmp.root, "absent"))

        assertEquals(0, fixture.restoreSessionParts(sessionsDir))
    }

    @Test
    fun `restoring copies the saved session parts into the directory intact`() {
        writePart("1000_a", "first part")
        writePart("2000_b", "second part")
        fixture.saveSessionParts(sessionsDir)
        sessionsDir.deleteRecursively()

        assertEquals(2, fixture.restoreSessionParts(sessionsDir))
        assertEquals(listOf("1000_a", "2000_b"), sessionsDir.list().orEmpty().sorted())
        assertEquals("first part", File(sessionsDir, "1000_a/metadata.pb").readText())
    }

    @Test
    fun `restoring is repeatable, as every iteration restores into an emptied directory`() {
        writePart("1000_a", "first part")
        fixture.saveSessionParts(sessionsDir)

        repeat(3) {
            sessionsDir.deleteRecursively()
            assertEquals(1, fixture.restoreSessionParts(sessionsDir))
            assertEquals(listOf("1000_a"), sessionsDir.list().orEmpty().toList())
        }
    }

    @Test
    fun `restoring with nothing saved copies nothing`() {
        assertEquals(0, fixture.restoreSessionParts(sessionsDir))
        assertEquals(emptyList<String>(), sessionsDir.list().orEmpty().toList())
    }

    private fun writePart(name: String, metadata: String) {
        File(sessionsDir, name).apply { mkdirs() }.resolve("metadata.pb").writeText(metadata)
    }
}
