package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceCompletedSpansTest {

    private companion object {
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )

        private val endedSpanProto = inFlightSpanProto.copy(
            end_time_unix_nano = 1726739283900000000L,
            status = SpanProto.Status.OK,
        )

        private val endedSpan = inFlightSpan.copy(
            endTimeNanos = 1726739283900000000L,
            status = Span.Status.OK,
        )

        private val secondEndedSpanProto = endedSpanProto.copy(span_id = "aaaaaaaaaaaaaaa5")

        private val secondEndedSpan = endedSpan.copy(spanId = "aaaaaaaaaaaaaaa5")

        /** Field 2 as a varint, which no version of the log has ever held. */
        private val UNKNOWN_FIELD = byteArrayOf(0x10, 0x01)

        /** Field 1 tagged with wire type 6, which is not a field encoding protobuf defines. */
        private val INVALID_FIELD_ENCODING = byteArrayOf(0x0E)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var manifestWriter: SessionManifestWriter
    private lateinit var metadataWriter: SessionMetadataWriter
    private lateinit var sessionSpanWriter: SessionSpanWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var sessionSpan: Span = fullyPopulatedSpan

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        sessionSpan = fullyPopulatedSpan
        activePart = partDirectory
        manifestWriter = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(lazy { sessionsDir }, { activePart }, { fullyPopulatedMetadata }, logger)
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `completed spans are reconstructed in the order they were appended`() {
        write(spans = listOf(endedSpanProto, secondEndedSpanProto))
        val spans = checkNotNull(service.reconstruct(partDirectory)?.data?.spans)
        assertEquals(listOf(endedSpan, secondEndedSpan, fullyPopulatedSpan), spans)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is reconstructed after the completed spans`() {
        write(spans = listOf(endedSpanProto))
        val spans = checkNotNull(service.reconstruct(partDirectory)?.data?.spans)
        assertEquals(fullyPopulatedSpan, spans.last())
        assertNoInternalErrors()
    }

    @Test
    fun `an empty completed spans log reconstructs the session span alone`() {
        write()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(fullyPopulatedSpan), payload.spans)
        assertNull(payload.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans are reconstructed when the session span never ended`() {
        val incomplete = fullyPopulatedSpan.copy(endTimeNanos = null)
        sessionSpan = incomplete
        write(spans = listOf(endedSpanProto))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(endedSpan), payload.spans)
        assertEquals(listOf(incomplete), payload.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `a logged span with no end time is still reconstructed as a completed span`() {
        write(spans = listOf(inFlightSpanProto))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(inFlightSpan, fullyPopulatedSpan), payload.spans)
        assertNull(payload.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part directory reconstructs its own completed spans`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)
        write(spans = listOf(endedSpanProto))
        write(other, spans = listOf(secondEndedSpanProto))

        assertEquals(endedSpan, service.reconstruct(partDirectory)?.data?.spans?.first())
        assertEquals(secondEndedSpan, service.reconstruct(other)?.data?.spans?.first())
        assertNoInternalErrors()
    }

    @Test
    fun `a torn final record does not lose the records before it`() {
        write(spans = listOf(endedSpanProto, secondEndedSpanProto))
        truncateLastByte()

        val spans = checkNotNull(service.reconstruct(partDirectory)?.data?.spans)
        assertEquals(listOf(endedSpan, fullyPopulatedSpan), spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a log holding nothing but a torn record reconstructs the session span alone`() {
        write(spans = listOf(endedSpanProto))
        truncateLastByte()
        assertEquals(listOf(fullyPopulatedSpan), service.reconstruct(partDirectory)?.data?.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a field a later SDK added is skipped rather than failing the log`() {
        write(spans = listOf(endedSpanProto))
        completedSpansFile().appendBytes(UNKNOWN_FIELD)
        completedSpansFile().appendBytes(completedSpansLog(listOf(secondEndedSpanProto)))

        val spans = checkNotNull(service.reconstruct(partDirectory)?.data?.spans)
        assertEquals(listOf(endedSpan, secondEndedSpan, fullyPopulatedSpan), spans)
        assertNoInternalErrors()
    }

    @Test
    fun `an invalid field encoding behind an intact record is reported and does not throw`() {
        write(spans = listOf(endedSpanProto))
        completedSpansFile().appendBytes(INVALID_FIELD_ENCODING)
        completedSpansFile().appendBytes(completedSpansLog(listOf(secondEndedSpanProto)))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a completed spans log holding undecodable bytes is reported and does not throw`() {
        write()
        completedSpansFile().writeBytes(INVALID_FIELD_ENCODING)
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a missing completed spans log is reported`() {
        writeManifest()
        writeMetadata()
        writeSessionSpan()
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a directory occupying the completed spans path is reported`() {
        writeManifest()
        writeMetadata()
        writeSessionSpan()
        completedSpansFile().mkdirs()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun completedSpansFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), COMPLETED_SPANS_FILE_NAME)

    private fun truncateLastByte(directory: SessionPartDirectory = partDirectory) {
        val bytes = completedSpansFile(directory).readBytes()
        completedSpansFile(directory).writeBytes(bytes.copyOf(bytes.size - 1))
    }

    private fun write(
        directory: SessionPartDirectory = partDirectory,
        spans: List<SpanProto> = emptyList(),
    ) {
        writeManifest(directory)
        writeMetadata(directory)
        writeSessionSpan(directory)
        writeCompletedSpans(directory, spans)
    }

    private fun writeManifest(directory: SessionPartDirectory = partDirectory) {
        assertTrue(manifestWriter.write(directory, fullyPopulatedResource, ENVELOPE_VERSION, ENVELOPE_TYPE))
    }

    private fun writeMetadata(directory: SessionPartDirectory = partDirectory) {
        activePart = directory
        assertTrue(metadataWriter.write())
    }

    private fun writeSessionSpan(directory: SessionPartDirectory = partDirectory) {
        activePart = directory
        assertTrue(sessionSpanWriter.write(sessionSpan))
    }

    private fun writeCompletedSpans(
        directory: SessionPartDirectory = partDirectory,
        spans: List<SpanProto> = emptyList(),
    ) {
        completedSpansFile(directory).writeBytes(completedSpansLog(spans))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }
}
