package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceMetadataTest {

    private companion object {
        private const val METADATA_FILE_NAME = "metadata.pb"
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
    private var metadataProvider: () -> EnvelopeMetadata = { fullyPopulatedMetadata }

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        metadataProvider = { fullyPopulatedMetadata }
        activePart = partDirectory
        manifestWriter = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(lazy { sessionsDir }, { activePart }, { metadataProvider() }, logger)
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `metadata is reconstructed from the session part directory`() {
        write()
        assertEquals(fullyPopulatedMetadata, service.reconstruct(partDirectory)?.metadata)
        assertNoInternalErrors()
    }

    @Test
    fun `metadata with no populated fields is reconstructed`() {
        metadataProvider = { EnvelopeMetadata() }
        write()
        assertEquals(EnvelopeMetadata(), service.reconstruct(partDirectory)?.metadata)
        assertNoInternalErrors()
    }

    @Test
    fun `the latest metadata is reconstructed after the user info changes`() {
        write()
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "newUserId", personas = linkedSetOf("payer")) }
        writeMetadata()

        with(checkNotNull(service.reconstruct(partDirectory)?.metadata)) {
            assertEquals("newUserId", userId)
            assertEquals(setOf("payer"), personas)
        }
        assertNoInternalErrors()
    }

    @Test
    fun `each session part directory reconstructs its own metadata`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)
        write()
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "otherUserId") }
        write(other)

        assertEquals("userId", service.reconstruct(partDirectory)?.metadata?.userId)
        assertEquals("otherUserId", service.reconstruct(other)?.metadata?.userId)
        assertNoInternalErrors()
    }

    @Test
    fun `missing metadata is reported`() {
        writeManifest()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a directory occupying the metadata path is reported`() {
        writeManifest()
        metadataFile().mkdirs()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `truncated metadata is reported and does not throw`() {
        write()
        val bytes = metadataFile().readBytes()
        metadataFile().writeBytes(bytes.copyOf(bytes.size / 2))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `metadata holding arbitrary bytes is reported and does not throw`() {
        write()
        metadataFile().writeBytes(byteArrayOf(-1, -1, -1, -1, -1, -1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an empty metadata file is reported`() {
        write()
        metadataFile().writeBytes(byteArrayOf())

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `metadata holding no format version is reported`() {
        write()
        writeMetadataBytes(fullyPopulatedMetadataProto.copy(format_version = 0))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an unsupported metadata format version is reported`() {
        write()
        writeMetadataBytes(fullyPopulatedMetadataProto.copy(format_version = FORMAT_VERSION + 1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun metadataFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), METADATA_FILE_NAME)

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
        assertTrue(sessionSpanWriter.write(fullyPopulatedSpan))
    }

    private fun writeMetadataBytes(metadata: EnvelopeMetadataProto, directory: SessionPartDirectory = partDirectory) {
        metadataFile(directory).writeBytes(EnvelopeMetadataProto.ADAPTER.encode(metadata))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }
}
