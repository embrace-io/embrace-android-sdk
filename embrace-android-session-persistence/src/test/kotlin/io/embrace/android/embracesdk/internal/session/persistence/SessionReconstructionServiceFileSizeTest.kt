package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceFileSizeTest {

    private companion object {
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val SESSION_SPAN_FILE_NAME = "session_span.pb"
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val PADDING_FIELD_NUMBER = 1000
        private const val PADDING_FIELD_OVERHEAD = 6

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var manifestWriter: SessionManifestWriter
    private lateinit var metadataWriter: SessionMetadataWriter
    private lateinit var sessionSpanWriter: SessionSpanWriter
    private lateinit var snapshotsWriter: SpanSnapshotsWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        activePart = partDirectory
        manifestWriter = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(lazy { sessionsDir }, { activePart }, { fullyPopulatedMetadata }, logger)
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        snapshotsWriter = SpanSnapshotsWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        File(sessionsDir, partDirectory.dirName).mkdirs()
        write()
    }

    @Test
    fun `a session part within the maximum file size is reconstructed`() {
        assertNotNull(service.reconstruct(partDirectory))
        assertNoInternalErrors()
    }

    @Test
    fun `an oversized manifest is rejected`() {
        assertOversizedFileRejected(MANIFEST_FILE_NAME)
    }

    @Test
    fun `oversized metadata is rejected`() {
        assertOversizedFileRejected(METADATA_FILE_NAME)
    }

    @Test
    fun `an oversized session span is rejected`() {
        assertOversizedFileRejected(SESSION_SPAN_FILE_NAME)
    }

    @Test
    fun `oversized span snapshots are rejected`() {
        assertOversizedFileRejected(SPAN_SNAPSHOTS_FILE_NAME)
    }

    @Test
    fun `a file at exactly the maximum size is reconstructed`() {
        padToSize(MANIFEST_FILE_NAME, MAX_PART_FILE_BYTES)
        assertEquals(MAX_PART_FILE_BYTES, partFile(MANIFEST_FILE_NAME).length())
        assertNotNull(service.reconstruct(partDirectory))
        assertNoInternalErrors()
    }

    /**
     * The log is decoded a record at a time rather than as one message, so overall limits don't apply (yet).
     */
    @Test
    fun `an oversized completed spans log is not rejected`() {
        padToSize(COMPLETED_SPANS_FILE_NAME, MAX_PART_FILE_BYTES + 1)
        assertNotNull(service.reconstruct(partDirectory))
        assertNoInternalErrors()
    }

    private fun assertOversizedFileRejected(fileName: String) {
        padToSize(fileName, MAX_PART_FILE_BYTES + 1)
        assertNull(service.reconstruct(partDirectory))
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }

    /**
     * Grows a part file to exactly [size] bytes by appending a length delimited field that no
     * version of the schema has held, so the padded file still decodes.
     */
    private fun padToSize(fileName: String, size: Long) {
        val file = partFile(fileName)
        val payloadSize = size - file.length() - PADDING_FIELD_OVERHEAD
        val padding = Buffer()
        ProtoAdapter.BYTES.encodeWithTag(
            ProtoWriter(padding),
            PADDING_FIELD_NUMBER,
            ByteArray(payloadSize.toInt()).toByteString(),
        )
        file.appendBytes(padding.readByteArray())
        assertEquals(size, file.length())
    }

    private fun partFile(fileName: String): File = File(File(sessionsDir, partDirectory.dirName), fileName)

    private fun write() {
        assertTrue(manifestWriter.write(partDirectory, fullyPopulatedResource, ENVELOPE_VERSION, ENVELOPE_TYPE))
        assertTrue(metadataWriter.write())
        assertTrue(sessionSpanWriter.write(fullyPopulatedSpan))
        assertTrue(snapshotsWriter.write(listOf(inFlightSpan)))
        partFile(COMPLETED_SPANS_FILE_NAME).writeBytes(completedSpansLog(emptyList()))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
