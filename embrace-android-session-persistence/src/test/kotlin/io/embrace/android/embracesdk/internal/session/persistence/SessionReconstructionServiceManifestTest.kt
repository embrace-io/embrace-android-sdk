package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
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

internal class SessionReconstructionServiceManifestTest {

    private companion object {
        private const val MANIFEST_FILE_NAME = "manifest.pb"
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
    private lateinit var writer: SessionManifestWriter
    private lateinit var metadataWriter: SessionMetadataWriter
    private lateinit var sessionSpanWriter: SessionSpanWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        activePart = partDirectory
        writer = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(lazy { sessionsDir }, { activePart }, { fullyPopulatedMetadata }, logger)
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun manifestFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), MANIFEST_FILE_NAME)

    private fun write(
        directory: SessionPartDirectory = partDirectory,
        resource: EnvelopeResource = fullyPopulatedResource,
        envelopeVersion: String = ENVELOPE_VERSION,
        envelopeType: String = ENVELOPE_TYPE,
        sharedLibSymbolMapping: Map<String, String>? = null,
    ) {
        assertTrue(writer.write(directory, resource, envelopeVersion, envelopeType, sharedLibSymbolMapping))
        activePart = directory
        assertTrue(metadataWriter.write())
        assertTrue(sessionSpanWriter.write(fullyPopulatedSpan))
    }

    private fun writeManifestBytes(manifest: SessionManifest, directory: SessionPartDirectory = partDirectory) {
        manifestFile(directory).writeBytes(SessionManifest.ADAPTER.encode(manifest))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }

    @Test
    fun `envelope is reconstructed from the manifest`() {
        write()

        val envelope = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(fullyPopulatedResource, envelope.resource)
        assertEquals(ENVELOPE_VERSION, envelope.version)
        assertEquals(ENVELOPE_TYPE, envelope.type)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is the only telemetry reconstructed so far`() {
        write()

        val envelope = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(listOf(fullyPopulatedSpan), envelope.data.spans)
        assertNull(envelope.data.spanSnapshots)
    }

    @Test
    fun `resource with no populated fields is reconstructed`() {
        write(resource = EnvelopeResource())
        assertEquals(EnvelopeResource(), service.reconstruct(partDirectory)?.resource)
        assertNoInternalErrors()
    }

    @Test
    fun `absent symbol mapping is reconstructed as null`() {
        write(sharedLibSymbolMapping = null)
        assertNull(service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `empty symbol mapping is reconstructed as an empty map`() {
        write(sharedLibSymbolMapping = emptyMap())
        assertEquals(emptyMap<String, String>(), service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `populated symbol mapping is reconstructed`() {
        val symbols = mapOf("armeabi-v7a" to "my-symbols", "x86" to "other-symbols")
        write(sharedLibSymbolMapping = symbols)
        assertEquals(symbols, service.reconstruct(partDirectory)?.data?.sharedLibSymbolMapping)
    }

    @Test
    fun `session part with empty ids is reconstructed`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        write(directory = directory)

        assertNotNull(service.reconstruct(directory))
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
        write(directory = other, resource = EnvelopeResource(appVersion = "9.9.9"), envelopeType = "logs")

        assertEquals(fullyPopulatedResource, service.reconstruct(partDirectory)?.resource)
        with(checkNotNull(service.reconstruct(other))) {
            assertEquals(EnvelopeResource(appVersion = "9.9.9"), resource)
            assertEquals("logs", type)
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
    fun `missing manifest is reported`() {
        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a directory occupying the manifest path is reported`() {
        manifestFile().mkdirs()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a truncated manifest is reported and does not throw`() {
        write()
        val bytes = manifestFile().readBytes()
        manifestFile().writeBytes(bytes.copyOf(bytes.size / 2))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a manifest holding arbitrary bytes is reported and does not throw`() {
        manifestFile().writeBytes(byteArrayOf(-1, -1, -1, -1, -1, -1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an empty manifest is reported`() {
        manifestFile().writeBytes(byteArrayOf())

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an unsupported format version is reported`() {
        write()
        writeManifestBytes(
            SessionManifest(
                format_version = FORMAT_VERSION + 1,
                envelope_version = ENVELOPE_VERSION,
                envelope_type = ENVELOPE_TYPE,
                resource = fullyPopulatedResourceProto,
            ),
        )

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a manifest with no resource is reported`() {
        write()
        writeManifestBytes(
            SessionManifest(
                format_version = FORMAT_VERSION,
                envelope_version = ENVELOPE_VERSION,
                envelope_type = ENVELOPE_TYPE,
            ),
        )

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }
}
