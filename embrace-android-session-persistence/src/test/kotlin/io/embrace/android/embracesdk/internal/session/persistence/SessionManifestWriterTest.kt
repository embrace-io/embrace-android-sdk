package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionManifestWriterTest {

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

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        writer = SessionManifestWriter(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun manifestFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), MANIFEST_FILE_NAME)

    private fun readManifest(directory: SessionPartDirectory = partDirectory): SessionManifest =
        manifestFile(directory).inputStream().use(SessionManifest.ADAPTER::decode)

    private fun write(
        directory: SessionPartDirectory = partDirectory,
        resource: EnvelopeResource = fullyPopulatedResource,
        envelopeVersion: String = ENVELOPE_VERSION,
        envelopeType: String = ENVELOPE_TYPE,
        sharedLibSymbolMapping: Map<String, String>? = null,
    ): Boolean = writer.write(directory, resource, envelopeVersion, envelopeType, sharedLibSymbolMapping)

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionManifestWriteFail", logger.internalErrorMessages.single().msg)
    }

    @Test
    fun `manifest is written into the session part directory`() {
        assertTrue(write())
        assertTrue(manifestFile().isFile)
        assertNoInternalErrors()
    }

    @Test
    fun `every envelope resource field is persisted`() {
        write()

        val resource = checkNotNull(readManifest().resource)
        assertEquals(fullyPopulatedResourceProto, resource)
        assertEquals(EnvelopeResourceProto.AppFramework.UNITY, resource.app_framework)
        assertEquals(53, resource.sdk_simple_version)
        assertEquals(8, resource.num_cores)
        assertEquals(true, resource.jailbroken)
        assertEquals(true, resource.uses_emmc_storage)
        assertEquals(123456789L, resource.disk_total_capacity)
        assertEquals(
            mapOf("custom.key" to "custom.value", "other.key" to "other.value"),
            resource.extras,
        )
    }

    @Test
    fun `null resource fields are persisted as absent`() {
        write(resource = EnvelopeResource())

        val resource = checkNotNull(readManifest().resource)
        assertEquals(EnvelopeResourceProto(), resource)
        assertNull(resource.app_version)
        assertNull(resource.app_framework)
        assertNull(resource.jailbroken)
        assertNull(resource.disk_total_capacity)
        assertNull(resource.num_cores)
        assertEquals(emptyMap<String, String>(), resource.extras)
    }

    @Test
    fun `envelope version type session ids and format version are persisted`() {
        write()

        with(readManifest()) {
            assertEquals(1, format_version)
            assertEquals(ENVELOPE_VERSION, envelope_version)
            assertEquals(ENVELOPE_TYPE, envelope_type)
            assertEquals(USER_SESSION_ID, user_session_id)
            assertEquals(SESSION_PART_ID, session_part_id)
        }
    }

    @Test
    fun `empty session ids are persisted as empty strings`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        assertTrue(write(directory = directory))

        // the 'none' token in the directory name must not leak into the manifest
        with(readManifest(directory)) {
            assertEquals("", user_session_id)
            assertEquals("", session_part_id)
        }
    }

    @Test
    fun `absent symbol mapping is persisted as absent`() {
        write(sharedLibSymbolMapping = null)
        assertNull(readManifest().shared_lib_symbol_mapping)
    }

    @Test
    fun `empty symbol mapping is persisted as an empty message`() {
        write(sharedLibSymbolMapping = emptyMap())
        assertEquals(SharedLibSymbolMapping(), readManifest().shared_lib_symbol_mapping)
    }

    @Test
    fun `populated symbol mapping is persisted`() {
        val symbols = mapOf("armeabi-v7a" to "my-symbols", "x86" to "other-symbols")
        write(sharedLibSymbolMapping = symbols)
        assertEquals(SharedLibSymbolMapping(symbols = symbols), readManifest().shared_lib_symbol_mapping)
    }

    @Test
    fun `manifest is rewritten on a second call`() {
        assertTrue(write(sharedLibSymbolMapping = mapOf("armeabi-v7a" to "my-symbols")))
        assertTrue(
            write(
                resource = EnvelopeResource(appVersion = "9.9.9", appFramework = AppFramework.FLUTTER),
                envelopeVersion = "9.9.9",
                envelopeType = "logs",
                sharedLibSymbolMapping = null,
            ),
        )

        with(readManifest()) {
            assertEquals("9.9.9", resource?.app_version)
            assertEquals(EnvelopeResourceProto.AppFramework.FLUTTER, resource?.app_framework)
            assertEquals("9.9.9", envelope_version)
            assertEquals("logs", envelope_type)
            assertNull(shared_lib_symbol_mapping)
            assertEquals(USER_SESSION_ID, user_session_id)
            assertEquals(SESSION_PART_ID, session_part_id)
        }
        assertEquals(listOf(MANIFEST_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `no temporary files are left behind`() {
        write()
        assertEquals(listOf(MANIFEST_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `each session part directory gets its own manifest`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)

        assertTrue(write())
        assertTrue(write(directory = other))
        assertEquals(SESSION_PART_ID, readManifest().session_part_id)
        assertEquals(other.sessionPartId, readManifest(other).session_part_id)
        assertNoInternalErrors()
    }

    @Test
    fun `missing session part directory is reported and does not throw`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        assertFalse(write(directory = absent))
        assertFalse(partDir(absent).exists())
        assertWriteFailureTracked()
    }

    @Test
    fun `a file occupying the session part path is reported and left untouched`() {
        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        val occupyingFile = partDir(occupied).apply { writeText("not a directory") }

        assertFalse(write(directory = occupied))
        assertWriteFailureTracked()
        assertEquals("not a directory", occupyingFile.readText())
    }

    @Test
    fun `failure building the manifest leaves no files on disk`() {
        assertFalse(write(sharedLibSymbolMapping = ExplodingMap()))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    /**
     * A map that fails when it is read, standing in for any input that blows up while the manifest
     * is being built.
     */
    private class ExplodingMap : Map<String, String> by mapOf("armeabi-v7a" to "my-symbols") {
        override val entries: Set<Map.Entry<String, String>>
            get() = error("boom")
    }
}
