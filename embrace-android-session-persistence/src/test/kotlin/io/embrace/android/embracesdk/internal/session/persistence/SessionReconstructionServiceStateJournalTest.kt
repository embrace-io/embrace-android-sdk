package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceStateJournalTest {

    private companion object {
        private val partDirectory = SessionPartDirectory(
            timestamp = 1726739283136L,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var service: SessionReconstructionService

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        service = SessionReconstructionService(lazy { sessionsDir }, errorHandler)
        File(sessionsDir, partDirectory.dirName).mkdirs()
        val metadataWriter = SessionMetadataWriter(
            target = SessionPartWriteTarget(lazy { sessionsDir }, FakeClock()) { partDirectory },
            metadataSource = { fullyPopulatedMetadata },
            resourceSource = { fullyPopulatedResource },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            errorHandler = errorHandler,
        )
        assertTrue(metadataWriter.write())
        File(partDir(), SPAN_SNAPSHOTS_FILE_NAME).writeBytes(spanCollectionHeader())
        File(partDir(), COMPLETED_SPANS_FILE_NAME).writeBytes(spanCollectionHeader())
    }

    @Test
    fun `a part with a journal returns its records`() {
        journalFile().writeBytes(stateJournalLog(listOf(stateRecord(1), stateRecord(2)), writtenAtMs = 10L))

        val part = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(listOf(stateRecord(1), stateRecord(2)), part.stateJournal)
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }

    @Test
    fun `the journal write time counts towards the last written time`() {
        val baseline = checkNotNull(service.reconstruct(partDirectory)?.lastWrittenMs)
        journalFile().writeBytes(stateJournalLog(listOf(stateRecord(1)), writtenAtMs = baseline + 5_000L))

        assertEquals(baseline + 5_000L, service.reconstruct(partDirectory)?.lastWrittenMs)
    }

    @Test
    fun `a part without a journal returns an empty list`() {
        val part = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(emptyList<StateRecord>(), part.stateJournal)
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }

    @Test
    fun `an unreadable journal does not fail the part`() {
        journalFile().writeBytes(byteArrayOf(0x0E))

        val part = service.reconstruct(partDirectory)
        assertNotNull(part)
        assertEquals(emptyList<StateRecord>(), part?.stateJournal)
        assertTrue(errorHandler.sdkErrorMessages.isNotEmpty())
        assertEquals(setOf("SessionReconstructionFail"), errorHandler.sdkErrorMessages.map { it.msg }.toSet())
    }

    private fun partDir(): File = File(sessionsDir, partDirectory.dirName)

    private fun journalFile(): File = File(partDir(), STATE_JOURNAL_FILE_NAME)
}
