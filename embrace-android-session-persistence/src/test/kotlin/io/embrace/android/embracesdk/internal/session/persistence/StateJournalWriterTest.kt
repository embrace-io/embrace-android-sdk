package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class StateJournalWriterTest {

    private companion object {
        private val partDirectory = SessionPartDirectory(
            timestamp = 1726739283136L,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )

        private val otherDirectory = SessionPartDirectory(timestamp = 1726739283137L, uuid = "other")
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var writer: StateJournalWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Volatile
    private var failing = false

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        clock = FakeClock()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        activePart = partDirectory
        writer = StateJournalWriter(target(), errorHandler)
        createPartDir(partDirectory)
    }

    @Test
    fun `appends leave earlier records in place and readable in order`() {
        assertTrue(writer.write(listOf(stateRecord(1), stateRecord(2))))
        clock.tick(1000)
        assertTrue(writer.write(listOf(stateRecord(3))))
        clock.tick(1000)
        assertTrue(writer.write(listOf(stateRecord(4))))

        assertEquals(listOf(1L, 2L, 3L, 4L), readJournal().records.map { it.sequence })
        assertNoSdkErrors()
    }

    @Test
    fun `earlier bytes are never rewritten`() {
        writer.write(listOf(stateRecord(1)))
        val before = journalFile().readBytes()
        writer.write(listOf(stateRecord(2)))
        val after = journalFile().readBytes()
        assertTrue(after.copyOf(before.size).contentEquals(before))
    }

    @Test
    fun `the header is written once`() {
        writer.write(listOf(stateRecord(1)))
        writer.close()
        writer.write(listOf(stateRecord(2)))
        val journal = StateJournal.ADAPTER.decode(journalFile().readBytes())
        assertEquals(FORMAT_VERSION, journal.format_version)
        assertEquals(2, journal.records.size)
        val headerCount = journalFile().readBytes().asList().windowed(2).count {
            it == listOf<Byte>(0x08, FORMAT_VERSION.toByte())
        }
        assertEquals(1, headerCount)
    }

    @Test
    fun `each write ends with the write time`() {
        writer.write(listOf(stateRecord(1)))
        assertEquals(clock.now(), readJournal().lastWrittenMs)
        clock.tick(500)
        writer.write(listOf(stateRecord(2)))
        assertEquals(clock.now(), readJournal().lastWrittenMs)
        val expectedTail = StateJournal.ADAPTER.encode(StateJournal(written_at_ms = clock.now()))
        val bytes = journalFile().readBytes()
        assertTrue(bytes.copyOfRange(bytes.size - expectedTail.size, bytes.size).contentEquals(expectedTail))
    }

    @Test
    fun `an empty list writes nothing`() {
        assertTrue(writer.write(emptyList()))
        assertFalse(journalFile().exists())
        assertNoSdkErrors()
    }

    @Test
    fun `a new directory starts a new file`() {
        createPartDir(otherDirectory)
        writer.write(listOf(stateRecord(1)))
        activePart = otherDirectory
        writer.write(listOf(stateRecord(2)))
        activePart = partDirectory
        writer.write(listOf(stateRecord(3)))

        assertEquals(listOf(1L, 3L), readJournal().records.map { it.sequence })
        assertEquals(listOf(2L), readJournal(otherDirectory).records.map { it.sequence })
        assertEquals(listOf(STATE_JOURNAL_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `nothing is written when no session part is active`() {
        activePart = null
        assertFalse(writer.write(listOf(stateRecord(1))))
        assertFalse(journalFile().exists())
        assertNoSdkErrors()
    }

    @Test
    fun `nothing is journaled after a record that is too large`() {
        val big = stateRecord(2, padding = 4096)
        val bound = StateJournal.ADAPTER.encodedSize(StateJournal(records = listOf(big))) - 1L
        writer = StateJournalWriter(target(), errorHandler, MAX_PART_FILE_BYTES, bound)

        assertFalse(writer.write(listOf(stateRecord(1), big, stateRecord(3))))
        assertFalse(writer.write(listOf(stateRecord(4))))

        assertEquals(listOf(1L), readJournal().records.map { it.sequence })
        assertWriteFailureTracked()
    }

    @Test
    fun `nothing is journaled after the journal fills up even if it would fit`() {
        val big = stateRecord(1, padding = 4096)
        val small = stateJournalAppend(listOf(stateRecord(3)), clock.now()).size
        val full = JOURNAL_HEADER_SIZE + stateJournalAppend(listOf(big), clock.now()).size
        writer = StateJournalWriter(target(), errorHandler, (full + small).toLong())

        assertFalse(writer.write(listOf(big, stateRecord(2, padding = 4096))))
        repeat(3) { assertFalse(writer.write(listOf(stateRecord(3)))) }

        assertEquals(listOf(1L), readJournal().records.map { it.sequence })
        assertEquals(full.toLong(), journalFile().length())
        assertWriteFailureTracked()
    }

    @Test
    fun `nothing is journaled after a write fails`() {
        assertTrue(writer.write(listOf(stateRecord(1))))
        failing = true
        assertFalse(writer.write(listOf(stateRecord(2))))
        failing = false
        assertWriteFailureTracked()

        assertFalse(writer.write(listOf(stateRecord(3))))
        assertEquals(listOf(1L), readJournal().records.map { it.sequence })
        assertWriteFailureTracked()
    }

    @Test
    fun `a journal that stopped does not stop the journal of the next session part`() {
        createPartDir(otherDirectory)
        assertTrue(writer.write(listOf(stateRecord(1))))
        failing = true
        assertFalse(writer.write(listOf(stateRecord(2))))
        failing = false
        activePart = otherDirectory
        assertTrue(writer.write(listOf(stateRecord(3))))

        assertEquals(listOf(1L), readJournal().records.map { it.sequence })
        assertEquals(listOf(3L), readJournal(otherDirectory).records.map { it.sequence })
    }

    @Test
    fun `a missing session part directory is reported`() {
        activePart = otherDirectory
        assertFalse(writer.write(listOf(stateRecord(1))))
        assertWriteFailureTracked()
    }

    private fun target(): SessionPartWriteTarget = SessionPartWriteTarget(
        sessionsDir = lazy { sessionsDir },
        clock = clock,
        sessionPartDirectorySource = { if (failing) error("boom") else activePart },
    )

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File = File(sessionsDir, directory.dirName)

    private fun journalFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), STATE_JOURNAL_FILE_NAME)

    private fun readJournal(directory: SessionPartDirectory = partDirectory): DecodedStateJournal =
        journalFile(directory).source().buffer().use { readStateJournal(it) }

    private fun assertNoSdkErrors() {
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, errorHandler.sdkErrorMessages.size)
        assertEquals("StateJournalWriteFail", errorHandler.sdkErrorMessages.single().msg)
    }
}
