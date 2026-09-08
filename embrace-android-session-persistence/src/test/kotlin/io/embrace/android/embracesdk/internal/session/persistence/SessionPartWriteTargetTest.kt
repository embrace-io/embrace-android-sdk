package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartWriteTargetTest {

    private companion object {
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var target: SessionPartWriteTarget

    private var activePart: SessionPartDirectory? = partDirectory
    private val reported = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        activePart = partDirectory
        reported.clear()
        target = SessionPartWriteTarget(lazy { sessionsDir }) { activePart }
    }

    @Test
    fun `a directory on disk is resolved without reporting anything`() {
        val expected = createPartDir(partDirectory)
        assertEquals(partDirectory, target.directory)
        assertEquals(expected, partDir())
        assertFalse(target.failed)
        assertEquals(emptyList<Throwable>(), reported)
    }

    @Test
    fun `nothing is resolved when no session part is active`() {
        activePart = null
        assertNull(target.directory)
        assertFalse(target.failed)
        assertEquals(emptyList<Throwable>(), reported)
    }

    @Test
    fun `a missing directory fails the part and is reported`() {
        assertNull(partDir())
        assertTrue(target.failed)
        assertEquals(MISSING_PART_DIR_MSG, reported.single().message)
    }

    @Test
    fun `a file occupying the directory path fails the part`() {
        File(sessionsDir, partDirectory.dirName).writeText("not a directory")
        assertNull(partDir())
        assertTrue(target.failed)
        assertEquals(MISSING_PART_DIR_MSG, reported.single().message)
    }

    @Test
    fun `a failed part is only reported once however many writes follow`() {
        assertNull(partDir())
        repeat(20) {
            assertNull(target.directory)
        }
        assertEquals(1, reported.size)
    }

    @Test
    fun `a failed part is not resurrected by the directory appearing later`() {
        assertNull(partDir())
        createPartDir(partDirectory)

        assertNull(target.directory)
        assertTrue(target.failed)
        assertEquals(1, reported.size)
    }

    @Test
    fun `failing one part does not fail the next one`() {
        assertNull(partDir())

        val other = SessionPartDirectory(timestamp = TIMESTAMP + 1, uuid = UUID)
        createPartDir(other)
        activePart = other
        val next = SessionPartWriteTarget(lazy { sessionsDir }) { activePart }

        assertEquals(other, next.directory)
        assertEquals(File(sessionsDir, other.dirName), next.partDir(other, reported::add))
        assertFalse(next.failed)
        assertEquals(1, reported.size)
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    /**
     * Resolves the active part's directory, recording anything reported along the way.
     */
    private fun partDir(): File? {
        val directory = target.directory ?: return null
        return target.partDir(directory, reported::add)
    }
}
