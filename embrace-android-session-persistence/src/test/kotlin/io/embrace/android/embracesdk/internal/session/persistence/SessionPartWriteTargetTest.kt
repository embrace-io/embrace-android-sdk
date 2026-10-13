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
import java.io.IOException

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
    fun `a write failure is reported as itself while the part directory is there`() {
        createPartDir(partDirectory)
        val failure = IOException("disk full")

        target.reportWriteFailure(failure, reported::add)

        assertEquals(listOf(failure), reported)
        assertFalse(target.failed)
    }

    @Test
    fun `a write failure on a part directory that has gone is reported as the missing directory`() {
        val failure = IOException("stream closed")

        target.reportWriteFailure(failure, reported::add)

        // the directory going is why the write failed, and is what the part is given up for
        assertEquals(MISSING_PART_DIR_MSG, reported.single().message)
        assertTrue(target.failed)
    }

    @Test
    fun `a write failure is reported as itself when no session part is active`() {
        activePart = null
        val failure = IOException("disk full")

        target.reportWriteFailure(failure, reported::add)

        assertEquals(listOf(failure), reported)
        assertFalse(target.failed)
    }

    @Test
    fun `a write failure is reported as itself when the active part cannot be resolved`() {
        target = SessionPartWriteTarget(lazy { sessionsDir }) { error("boom") }
        val failure = IOException("disk full")

        target.reportWriteFailure(failure, reported::add)

        assertEquals(listOf(failure), reported)
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

    @Test
    fun `an atomic write records the part alive before it starts and once the file is in place`() {
        val partDir = createPartDir(partDirectory)
        val store = RecordingLastAliveStore()
        target = SessionPartWriteTarget(lazy { sessionsDir }, store) { activePart }

        writeAtomically(partDir, METADATA_FILE_NAME, maxBytes = 16, target = target) { it.write(byteArrayOf(1, 2)) }

        assertEquals(listOf(Recorded(METADATA_FILE_NAME, 0), Recorded(METADATA_FILE_NAME, 2)), store.recorded)
    }

    @Test
    fun `an append records the part alive before it starts and once the bytes are written`() {
        val partDir = createPartDir(partDirectory)
        val store = RecordingLastAliveStore()
        target = SessionPartWriteTarget(lazy { sessionsDir }, store) { activePart }
        val file = SpanCollectionFile(partDirectory, File(partDir, COMPLETED_SPANS_FILE_NAME), target)

        file.append(byteArrayOf(1, 2, 3))
        file.close()

        assertEquals(listOf(Recorded(COMPLETED_SPANS_FILE_NAME, 0), Recorded(COMPLETED_SPANS_FILE_NAME, 3)), store.recorded)
    }

    @Test
    fun `a write that fails still records the part alive before it started`() {
        val partDir = createPartDir(partDirectory)
        val store = RecordingLastAliveStore()
        target = SessionPartWriteTarget(lazy { sessionsDir }, store) { activePart }

        runCatching {
            writeAtomically(partDir, METADATA_FILE_NAME, maxBytes = 1, target = target) { it.write(byteArrayOf(1, 2)) }
        }

        assertEquals(listOf(Recorded(METADATA_FILE_NAME, 0)), store.recorded)
    }

    @Test
    fun `one recorder is opened for a part however many writes it takes`() {
        val partDir = createPartDir(partDirectory)
        val store = RecordingLastAliveStore()
        target = SessionPartWriteTarget(lazy { sessionsDir }, store) { activePart }

        repeat(3) {
            writeAtomically(partDir, METADATA_FILE_NAME, maxBytes = 16, target = target) { it.write(byteArrayOf(1)) }
        }

        assertEquals(listOf(partDir), store.openedFor)
        assertEquals(6, store.recorded.size)
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

    /**
     * A file a part's process was recorded alive writing, with the file's length at that moment, which shows
     * whether the write had happened yet.
     */
    private data class Recorded(val fileName: String, val length: Long)

    /**
     * Records each time a part's process was recorded alive, and the part directories it opened recorders for.
     */
    private class RecordingLastAliveStore : SessionPartLastAliveStore {
        val recorded = mutableListOf<Recorded>()
        val openedFor = mutableListOf<File>()

        override fun recorder(partDir: File): SessionPartLastAliveRecorder {
            openedFor += partDir
            return SessionPartLastAliveRecorder { file -> recorded += Recorded(file.name, file.length()) }
        }

        override fun lastAliveMs(partDir: File): Long? = null
    }
}
