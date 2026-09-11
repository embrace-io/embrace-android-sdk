package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    private lateinit var writer: SessionMetadataWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var metadataProvider: () -> EnvelopeMetadata = { fullyPopulatedMetadata }

    @Volatile
    private var resourceProvider: () -> EnvelopeResource = { fullyPopulatedResource }

    @Volatile
    private var symbolProvider: () -> Map<String, String>? = { null }

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        metadataProvider = { fullyPopulatedMetadata }
        resourceProvider = { fullyPopulatedResource }
        symbolProvider = { null }
        activePart = partDirectory
        writer = SessionMetadataWriter(
            target = SessionPartWriteTarget(lazy { sessionsDir }) { activePart },
            metadataSource = { metadataProvider() },
            resourceSource = { resourceProvider() },
            envelopeVersion = ENVELOPE_VERSION,
            envelopeType = ENVELOPE_TYPE,
            sharedLibSymbolMappingSource = { symbolProvider() },
            logger = logger,
        )
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `the envelope is reconstructed from the metadata`() {
        write()

        val envelope = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(fullyPopulatedResource, envelope.resource)
        assertEquals(fullyPopulatedMetadata, envelope.metadata)
        assertEquals(ENVELOPE_VERSION, envelope.version)
        assertEquals(ENVELOPE_TYPE, envelope.type)
        assertNoInternalErrors()
    }

    @Test
    fun `every persisted file contributes to the reconstructed telemetry`() {
        write()

        val envelope = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(listOf(fullyPopulatedSpan), envelope.data.spans)
        assertEquals(listOf(inFlightSpan), envelope.data.spanSnapshots)
    }

    @Test
    fun `metadata with no populated fields is reconstructed`() {
        metadataProvider = { EnvelopeMetadata() }
        write()
        assertEquals(EnvelopeMetadata(), service.reconstruct(partDirectory)?.metadata)
        assertNoInternalErrors()
    }

    @Test
    fun `a resource with no populated fields is reconstructed`() {
        resourceProvider = { EnvelopeResource() }
        write()
        assertEquals(EnvelopeResource(), service.reconstruct(partDirectory)?.resource)
        assertNoInternalErrors()
    }

    @Test
    fun `the latest metadata is reconstructed after the user info changes`() {
        write()
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "newUserId", personas = linkedSetOf("payer")) }
        assertTrue(writer.write())

        with(checkNotNull(service.reconstruct(partDirectory)?.metadata)) {
            assertEquals("newUserId", userId)
            assertEquals(setOf("payer"), personas)
        }
        assertNoInternalErrors()
    }

    @Test
    fun `absent symbol mapping is reconstructed as null`() {
        symbolProvider = { null }
        write()
        assertNull(service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `empty symbol mapping is reconstructed as an empty map`() {
        symbolProvider = { emptyMap() }
        write()
        assertEquals(emptyMap<String, String>(), service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `populated symbol mapping is reconstructed`() {
        val symbols = mapOf("armeabi-v7a" to "my-symbols", "x86" to "other-symbols")
        symbolProvider = { symbols }
        write()
        assertEquals(symbols, service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `a session part with empty ids is reconstructed`() {
        val anonymous = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(anonymous)
        write(anonymous)

        assertNotNull(service.reconstruct(anonymous))
        assertNoInternalErrors()
    }

    @Test
    fun `each session part directory is reconstructed independently`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)
        write()

        resourceProvider = { EnvelopeResource(appVersion = "9.9.9") }
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "otherUserId") }
        write(other)

        with(checkNotNull(service.reconstruct(partDirectory))) {
            assertEquals(fullyPopulatedResource, resource)
            assertEquals("userId", metadata?.userId)
        }
        with(checkNotNull(service.reconstruct(other))) {
            assertEquals(EnvelopeResource(appVersion = "9.9.9"), resource)
            assertEquals("otherUserId", metadata?.userId)
        }
        assertNoInternalErrors()
    }

    @Test
    fun `missing session part directory is reported and does not throw`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        assertNull(service.reconstruct(absent))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a file occupying the session part path is reported`() {
        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        partDir(occupied).writeText("not a directory")

        assertNull(service.reconstruct(occupied))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `missing metadata is reported`() {
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a directory occupying the metadata path is reported`() {
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
        metadataFile().writeBytes(byteArrayOf(-1, -1, -1, -1, -1, -1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an empty metadata file is reported`() {
        metadataFile().writeBytes(byteArrayOf())

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `metadata holding no format version is reported`() {
        write()
        writeMetadataBytes(fullyPopulatedMetadataProto().copy(format_version = 0))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an unsupported format version is reported`() {
        write()
        writeMetadataBytes(fullyPopulatedMetadataProto().copy(format_version = FORMAT_VERSION + 1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `metadata with no resource is reported`() {
        write()
        writeMetadataBytes(fullyPopulatedMetadataProto().copy(resource = null))

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
        activePart = directory
        assertTrue(writer.write())
        File(partDir(directory), "completed_spans.pb")
            .writeBytes(completedSpansLog(listOf(fullyPopulatedSpanProto)))
        File(partDir(directory), "span_snapshots.pb").writeBytes(
            SpanSnapshots.ADAPTER.encode(
                SpanSnapshots(format_version = FORMAT_VERSION, spans = listOf(inFlightSpanProto)),
            ),
        )
    }

    private fun writeMetadataBytes(metadata: SessionMetadata, directory: SessionPartDirectory = partDirectory) {
        metadataFile(directory).writeBytes(SessionMetadata.ADAPTER.encode(metadata))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }
}
