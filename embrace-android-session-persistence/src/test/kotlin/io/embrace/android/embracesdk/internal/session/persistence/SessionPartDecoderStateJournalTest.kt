package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartDecoderStateJournalTest {

    private companion object {
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val JOURNAL_WRITTEN_AT = FakeClock.DEFAULT_FAKE_CURRENT_TIME + 60_000L

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
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var decoder: SessionPartDecoder

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        decoder = SessionPartDecoder(errorHandler)
        File(sessionsDir, partDirectory.dirName).mkdirs()
        val metadataWriter = SessionMetadataWriter(
            target = target(FakeClock()),
            metadataSource = { fullyPopulatedMetadata },
            resourceSource = { fullyPopulatedResource },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            errorHandler = errorHandler,
        )
        assertTrue(metadataWriter.write())
        File(partDir(), COMPLETED_SPANS_FILE_NAME).writeBytes(completedSpansLog(listOf(journalSessionSpan.toProto())))
        File(partDir(), SPAN_SNAPSHOTS_FILE_NAME).writeBytes(spanSnapshotsRollup(listOf(inFlightSpanProto)))
    }

    @Test
    fun `no journal file gives exactly the envelope there was before journals`() {
        val part = checkNotNull(decoder.decode(FileSessionPartSource(partDir())))

        assertEquals(listOf(journalSessionSpan), part.envelope.data?.spans)
        assertEquals(listOf(inFlightSpan), part.envelope.data?.spanSnapshots)
        assertEquals(FakeClock.DEFAULT_FAKE_CURRENT_TIME, part.lastWrittenMs)
        assertNoSdkErrors()
    }

    @Test
    fun `a journal with no opening entry adds nothing`() {
        writeJournal(listOf(limitReached("net", 1)))

        val part = checkNotNull(decoder.decode(FileSessionPartSource(partDir())))
        assertEquals(listOf(journalSessionSpan), part.envelope.data?.spans)
        assertEquals(listOf(inFlightSpan), part.envelope.data?.spanSnapshots)
        assertEquals(JOURNAL_WRITTEN_AT, part.lastWrittenMs)
        assertNoSdkErrors()
    }

    @Test
    fun `a closed journal adds its span to the spans`() {
        writeJournal(listOf(opened(initialSeq = 2), change(3, "net", "wifi"), closed()))

        val part = checkNotNull(decoder.decode(FileSessionPartSource(partDir())))
        val data = checkNotNull(part.envelope.data)
        assertEquals(listOf(inFlightSpan), data.spanSnapshots)
        assertEquals(listOf(journalSessionSpan), data.spans?.dropLast(1))
        val span = checkNotNull(data.spans?.last())
        assertEquals("emb-states", span.name)
        assertEquals(journalSessionSpan.endTimeNanos, span.endTimeNanos)
        assertEquals(journalSessionSpan.startTimeNanos, span.startTimeNanos)
        assertEquals(JOURNAL_PART_ID, span.attributes?.single { it.key == "emb.session_part_id" }?.data)
        assertEquals(listOf("emb-state-net"), span.events?.map { it.name })
        assertEquals(JOURNAL_WRITTEN_AT, part.lastWrittenMs)
        assertNoSdkErrors()
    }

    @Test
    fun `a journal with no closing entry adds its span to the snapshots as truncated`() {
        writeJournal(listOf(opened(initialSeq = 2), change(3, "net", "wifi")))

        val part = checkNotNull(decoder.decode(FileSessionPartSource(partDir())))
        val data = checkNotNull(part.envelope.data)
        assertEquals(listOf(journalSessionSpan), data.spans)
        assertEquals(listOf(inFlightSpan), data.spanSnapshots?.dropLast(1))
        val span = checkNotNull(data.spanSnapshots?.last())
        assertEquals("emb-states", span.name)
        assertNull(span.endTimeNanos)
        assertEquals("true", span.attributes?.single { it.key == "emb.app.state.truncated" }?.data)
        assertNoSdkErrors()
    }

    @Test
    fun `a journal in a format that cannot be read does not fail the decode`() {
        File(partDir(), STATE_JOURNAL_FILE_NAME).writeBytes(
            StateJournalFile.ADAPTER.encode(StateJournalFile(format_version = FORMAT_VERSION + 1)),
        )

        val part = decoder.decode(FileSessionPartSource(partDir()))
        assertNotNull(part)
        assertEquals(listOf(journalSessionSpan), part?.envelope?.data?.spans)
        assertEquals(listOf(inFlightSpan), part?.envelope?.data?.spanSnapshots)
        assertEquals(listOf("SessionReconstructionFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a journal that is a directory instead of a file does not fail the decode`() {
        File(partDir(), STATE_JOURNAL_FILE_NAME).mkdirs()

        val part = decoder.decode(FileSessionPartSource(partDir()))
        assertEquals(listOf(journalSessionSpan), part?.envelope?.data?.spans)
        assertEquals(listOf(inFlightSpan), part?.envelope?.data?.spanSnapshots)
    }

    private fun writeJournal(records: List<io.embrace.android.embracesdk.internal.state.StateJournalRecord>) {
        val writer = StateJournalWriter(target(FakeClock(JOURNAL_WRITTEN_AT)), errorHandler)
        assertTrue(writer.write(records))
        writer.close()
    }

    private fun target(clock: FakeClock): SessionPartWriteTarget =
        SessionPartWriteTarget(lazy { sessionsDir }, clock) { partDirectory }

    private fun partDir(): File = File(sessionsDir, partDirectory.dirName)

    private fun assertNoSdkErrors() {
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }
}
