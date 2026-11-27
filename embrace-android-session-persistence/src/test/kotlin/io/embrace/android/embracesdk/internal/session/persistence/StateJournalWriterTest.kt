package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.random.Random

internal class StateJournalWriterTest {

    private companion object {
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val OTHER_PART_ID = "cccccccccccccccccccccccccccccccc"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = JOURNAL_USER_SESSION_ID,
            sessionPartId = JOURNAL_PART_ID,
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var writer: StateJournalWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        clock = FakeClock()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        activePart = partDirectory
        writer = StateJournalWriter(target(), errorHandler)
        File(sessionsDir, partDirectory.dirName).mkdirs()
    }

    @Test
    fun `all four kinds of record read back in order with awkward text intact`() {
        val records = listOf(
            opened(
                initialSeq = 4,
                states = listOf(
                    openingState("net", AWKWARD_VALUE),
                    openingState("power", "on", system = true, notInSession = 2),
                ),
            ),
            change(5, "net", AWKWARD_VALUE, system = true, attributes = mapOf("k;=\n" to AWKWARD_VALUE)),
            change(6, "power", "off"),
            limitReached("net", 2),
            limitReached(null, 50),
            closed(
                droppedChanges = 3,
                states = listOf(closingState("net", 3, finalValue = AWKWARD_VALUE, finalSystem = true, finalSeq = 9)),
            ),
        )
        assertTrue(writer.write(records))
        assertNoSdkErrors()

        val journal = read()
        val opened = checkNotNull(journal.opened)
        assertEquals(4L, opened.initial_seq)
        assertEquals(listOf("net", "power"), opened.states.map { it.name })
        assertEquals(listOf(AWKWARD_VALUE, "on"), opened.states.map { it.value_ })
        assertEquals(listOf(false, true), opened.states.map { it.system })
        assertEquals(listOf(0, 2), opened.states.map { it.not_in_session_count })

        assertEquals(listOf(5L, 6L), journal.changes.map { it.seq })
        val first = journal.changes.first()
        assertEquals("net", first.name)
        assertEquals(AWKWARD_VALUE, first.value_)
        assertTrue(first.system)
        assertEquals(1726739284005L, first.time_ms)
        assertEquals(listOf(AttributeProto("k;=\n", AWKWARD_VALUE)), first.attributes)
        assertFalse(journal.changes.last().system)

        assertEquals(listOf("net" to 2, "" to 50), journal.limits.map { it.name to it.limit })

        val closed = checkNotNull(journal.closed)
        assertEquals(3, closed.dropped_changes)
        val final = closed.states.single()
        assertEquals("net", final.name)
        assertEquals(3, final.dropped_count)
        assertTrue(final.has_final)
        assertEquals(AWKWARD_VALUE, final.final_value)
        assertTrue(final.final_system)
        assertEquals(9L, final.final_seq)
        assertEquals(clock.now(), journal.lastWrittenMs)
        assertNull(journal.corruption)
    }

    @Test
    fun `records appended in separate writes keep their order and one version header`() {
        assertTrue(writer.write(listOf(opened())))
        clock.tick(1000)
        assertTrue(writer.write(listOf(change(1, "net", "wifi"), change(2, "net", "4g"))))
        clock.tick(1000)
        assertTrue(writer.write(listOf(change(3, "net", "5g"))))

        val journal = read()
        assertEquals(listOf(1L, 2L, 3L), journal.changes.map { it.seq })
        assertEquals(clock.now(), journal.lastWrittenMs)
        assertEquals(listOf(STATE_JOURNAL_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `the ids are generated once, are valid, and are the same each time the file is read`() {
        writer.write(listOf(opened(), change(1, "net", "wifi")))

        val first = checkNotNull(read().opened)
        val second = checkNotNull(read().opened)
        assertEquals(first.trace_id, second.trace_id)
        assertEquals(first.span_id, second.span_id)
        assertTrue(Regex("[0-9a-f]{32}").matches(first.trace_id))
        assertTrue(Regex("[0-9a-f]{16}").matches(first.span_id))
        assertFalse(first.span_id.all { it == '0' })
        assertFalse(first.trace_id.all { it == '0' })

        val rendered = checkNotNull(renderChangeLogSpan(read(), null))
        val renderedAgain = checkNotNull(renderChangeLogSpan(read(), null))
        assertEquals(rendered.spanId, renderedAgain.spanId)
        assertEquals(rendered.traceId, renderedAgain.traceId)
    }

    @Test
    fun `an id that would be all zero is generated again`() {
        val zeros = object : Random() {
            private val real = Random(1)
            private var calls = 0
            override fun nextBits(
                bitCount: Int,
            ): Int = if (calls++ < 4 || calls in 9..10) 0 else real.nextBits(bitCount)
        }
        writer = StateJournalWriter(target(), errorHandler, random = zeros)
        writer.write(listOf(opened()))

        val opened = checkNotNull(read().opened)
        assertFalse(opened.trace_id.all { it == '0' })
        assertFalse(opened.span_id.all { it == '0' })
    }

    @Test
    fun `records for another session part are skipped`() {
        val other =
            listOf(opened(sessionPartId = OTHER_PART_ID), change(1, "net", "wifi", sessionPartId = OTHER_PART_ID))
        assertTrue(writer.write(other))
        assertFalse(journalFile().exists())

        val mixed = listOf(opened(), change(1, "net", "x", sessionPartId = OTHER_PART_ID), change(2, "net", "y"))
        assertTrue(writer.write(mixed))
        val journal = read()
        assertNotNull(journal.opened)
        assertEquals(listOf(2L), journal.changes.map { it.seq })
        assertNoSdkErrors()
    }

    @Test
    fun `nothing is written when there are no records or no active part`() {
        assertTrue(writer.write(emptyList()))
        activePart = null
        assertFalse(writer.write(listOf(opened())))
        assertFalse(journalFile().exists())
        assertNoSdkErrors()
    }

    @Test
    fun `a torn tail keeps every complete earlier record at every cut point and never throws`() {
        val ends = mutableListOf<Long>()
        val writtenAtBytes = writtenAtSize(clock.now())
        writer.write(listOf(opened(initialSeq = 1, states = listOf(openingState("net", "wifi")))))
        val openedEnd = journalFile().length() - writtenAtBytes
        val oneChange = listOf(change(2, "net", AWKWARD_VALUE, attributes = mapOf("a" to "b")))
        writer.write(oneChange)
        ends.add(journalFile().length() - writtenAtBytes)
        writer.write(listOf(change(3, "net", "5g")))
        ends.add(journalFile().length() - writtenAtBytes)
        writer.write(listOf(closed(droppedChanges = 1)))
        val closedEnd = journalFile().length() - writtenAtBytes
        val bytes = journalFile().readBytes()

        for (cut in 0..bytes.size) {
            val journal = readBytes(bytes.copyOf(cut))
            assertEquals("opened at $cut", cut >= openedEnd, journal.opened != null)
            assertEquals("changes at $cut", ends.count { cut >= it }, journal.changes.size)
            assertEquals("closed at $cut", cut >= closedEnd, journal.closed != null)
        }
    }

    @Test
    fun `a file with a wrong format version is not read`() {
        val bytes = StateJournalFile.ADAPTER.encode(
            StateJournalFile(
                format_version = FORMAT_VERSION + 1,
                entries = listOf(StateJournalEntry(opened = StateJournalOpened(trace_id = "t", span_id = "s"))),
            ),
        )
        assertThrows(IOException::class.java) { readBytes(bytes) }
    }

    @Test
    fun `entries beyond the size limit are dropped and reported once`() {
        val size = StateJournalFile.ADAPTER.encodedSize(StateJournalFile(format_version = FORMAT_VERSION))
        writer = StateJournalWriter(target(), errorHandler, maxBytes = size + 150L)
        assertTrue(writer.write(listOf(opened(), change(1, "net", "wifi"))))
        assertFalse(writer.write(listOf(change(2, "net", "x".repeat(300)))))
        assertFalse(writer.write(listOf(change(3, "net", "y".repeat(300)))))

        assertEquals(listOf(1L), read().changes.map { it.seq })
        assertEquals(listOf("StateJournalWriteFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `once a record cannot be written nothing more is appended, so the file never has a hole`() {
        val size = StateJournalFile.ADAPTER.encodedSize(StateJournalFile(format_version = FORMAT_VERSION))
        writer = StateJournalWriter(target(), errorHandler, maxBytes = size + 200L)
        assertTrue(writer.write(listOf(opened(), change(1, "net", "wifi"))))

        // the second change does not fit. The third would, and so would the record that closes the part
        assertFalse(writer.write(listOf(change(2, "net", "x".repeat(300)), change(3, "net", "4g"))))
        assertFalse(writer.write(listOf(change(4, "net", "5g"), closed())))

        val journal = read()
        assertEquals(listOf(1L), journal.changes.map { it.seq })
        assertNull(journal.closed)
    }

    @Test
    fun `a record larger than a single record may be stops the journal rather than being skipped`() {
        writer = StateJournalWriter(target(), errorHandler, maxRecordBytes = 100L)
        assertFalse(writer.write(listOf(opened(), change(1, "net", "x".repeat(300)), change(2, "net", "wifi"), closed())))

        val journal = read()
        assertNotNull(journal.opened)
        assertTrue(journal.changes.isEmpty())
        assertNull(journal.closed)
    }

    @Test
    fun `a missing part directory is reported as a write failure`() {
        partDir().deleteRecursively()
        assertFalse(writer.write(listOf(opened())))
        assertEquals(listOf("StateJournalWriteFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    private fun target(): SessionPartWriteTarget =
        SessionPartWriteTarget(lazy { sessionsDir }, clock) { activePart }

    private fun partDir(): File = File(sessionsDir, partDirectory.dirName)

    private fun journalFile(): File = File(partDir(), STATE_JOURNAL_FILE_NAME)

    private fun read(): DecodedStateJournal = journalFile().source().buffer().use { readStateJournal(it) }

    private fun readBytes(bytes: ByteArray): DecodedStateJournal {
        val file = File(tempFolder.root, "copy.pb").apply { writeBytes(bytes) }
        return file.source().buffer().use { readStateJournal(it) }
    }

    private fun assertNoSdkErrors() {
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }
}
