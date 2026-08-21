package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceSessionSpanTest {

    private companion object {
        private const val SESSION_SPAN_FILE_NAME = "session_span.pb"
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
    private var spanProvider: () -> Span = { fullyPopulatedSpan }

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        spanProvider = { fullyPopulatedSpan }
        activePart = partDirectory
        manifestWriter = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(lazy { sessionsDir }, { activePart }, { fullyPopulatedMetadata }, logger)
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `the session span is reconstructed from the session part directory`() {
        write()
        assertEquals(listOf(fullyPopulatedSpan), service.reconstruct(partDirectory)?.data?.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a complete session span is not reconstructed as a span snapshot`() {
        write()
        assertNull(service.reconstruct(partDirectory)?.data?.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `an incomplete session span is reconstructed as a span snapshot`() {
        val incomplete = fullyPopulatedSpan.copy(endTimeNanos = null)
        spanProvider = { incomplete }
        write()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(incomplete), payload.spanSnapshots)
        assertNull(payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span that ended at zero is reconstructed as a completed span`() {
        val ended = fullyPopulatedSpan.copy(endTimeNanos = 0)
        spanProvider = { ended }
        write()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(ended), payload.spans)
        assertNull(payload.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `a span with no populated fields is reconstructed as a span snapshot`() {
        spanProvider = { Span() }
        write()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(Span(status = Span.Status.UNSET)), payload.spanSnapshots)
        assertNull(payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `the latest session span is reconstructed after its attributes change`() {
        write()
        val updated = fullyPopulatedSpan.copy(
            attributes = listOf(Attribute(key = "emb.heartbeat_time_unix_nano", data = "1726739286136000000")),
        )
        spanProvider = { updated }
        writeSessionSpan()

        assertEquals(listOf(updated), service.reconstruct(partDirectory)?.data?.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part directory reconstructs its own session span`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)
        write()
        spanProvider = { fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9") }
        write(other)

        assertEquals("aaaaaaaaaaaaaaa1", service.reconstruct(partDirectory)?.data?.spans?.single()?.spanId)
        assertEquals("aaaaaaaaaaaaaaa9", service.reconstruct(other)?.data?.spans?.single()?.spanId)
        assertNoInternalErrors()
    }

    @Test
    fun `a missing session span is reported`() {
        writeManifest()
        writeMetadata()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a directory occupying the session span path is reported`() {
        writeManifest()
        writeMetadata()
        sessionSpanFile().mkdirs()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a truncated session span is reported and does not throw`() {
        write()
        val bytes = sessionSpanFile().readBytes()
        sessionSpanFile().writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a session span holding arbitrary bytes is reported and does not throw`() {
        write()
        sessionSpanFile().writeBytes(byteArrayOf(-1, -1, -1, -1, -1, -1))
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an empty session span file is reported`() {
        write()
        sessionSpanFile().writeBytes(byteArrayOf())

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a session span holding no format version is reported`() {
        write()
        writeSessionSpanBytes(fullyPopulatedSessionSpanProto.copy(format_version = 0))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an unsupported session span format version is reported`() {
        write()
        writeSessionSpanBytes(fullyPopulatedSessionSpanProto.copy(format_version = FORMAT_VERSION + 1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a session span file holding no span is reported`() {
        write()
        writeSessionSpanBytes(SessionPartSpan(format_version = FORMAT_VERSION, span = null))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun sessionSpanFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), SESSION_SPAN_FILE_NAME)

    private fun write(directory: SessionPartDirectory = partDirectory) {
        writeManifest(directory)
        writeMetadata(directory)
        writeSessionSpan(directory)
        writeCompletedSpans(directory)
        writeSpanSnapshots(directory)
    }

    private fun writeCompletedSpans(directory: SessionPartDirectory = partDirectory) {
        File(partDir(directory), "completed_spans.pb").writeBytes(completedSpansLog(emptyList()))
    }

    private fun writeSpanSnapshots(directory: SessionPartDirectory = partDirectory) {
        File(partDir(directory), "span_snapshots.pb").writeBytes(
            SpanSnapshots.ADAPTER.encode(SpanSnapshots(format_version = FORMAT_VERSION)),
        )
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
        assertTrue(sessionSpanWriter.write(spanProvider()))
    }

    private fun writeSessionSpanBytes(sessionSpan: SessionPartSpan, directory: SessionPartDirectory = partDirectory) {
        sessionSpanFile(directory).writeBytes(SessionPartSpan.ADAPTER.encode(sessionSpan))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }
}
