package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
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
import java.util.concurrent.CountDownLatch

internal class SessionMetadataWriterTest {

    private companion object {
        private const val METADATA_FILE_NAME = "metadata.pb"
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

    @Volatile
    private var metadataProvider: () -> EnvelopeMetadata = { fullyPopulatedMetadata }

    @Volatile
    private var resourceProvider: () -> EnvelopeResource = { fullyPopulatedResource }

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        metadataProvider = { fullyPopulatedMetadata }
        resourceProvider = { fullyPopulatedResource }
        activePart = partDirectory
        writer = SessionMetadataWriter(
            lazy { sessionsDir },
            { activePart },
            { metadataProvider() },
            { resourceProvider() },
            logger,
        )
        createPartDir(partDirectory)
    }

    @Test
    fun `metadata is written into the session part directory`() {
        assertTrue(write())
        assertTrue(metadataFile().isFile)
        assertNoInternalErrors()
    }

    @Test
    fun `every envelope metadata field is persisted`() {
        write()

        val proto = readMetadata()
        assertEquals(fullyPopulatedMetadataProto, proto)
        assertEquals("userId", proto.user_id)
        assertEquals("email@example.com", proto.email)
        assertEquals("username", proto.username)
        assertEquals(listOf("persona1", "persona2"), proto.personas)
        assertEquals("Europe/London", proto.timezone_description)
        assertEquals("en_GB", proto.locale)
    }

    @Test
    fun `the format version is persisted even when nothing else is populated`() {
        metadataProvider = { EnvelopeMetadata() }
        write()

        assertEquals(FORMAT_VERSION, readMetadata().format_version)
    }

    @Test
    fun `null user fields are persisted as absent`() {
        metadataProvider = { EnvelopeMetadata(timezoneDescription = "Europe/London", locale = "en_GB") }
        write()

        val proto = readMetadata()
        assertNull(proto.user_id)
        assertNull(proto.email)
        assertNull(proto.username)
        assertEquals(emptyList<String>(), proto.personas)
    }

    @Test
    fun `metadata is overwritten when the user info changes`() {
        write()
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "newUserId", personas = linkedSetOf("payer")) }
        assertTrue(writer.write())

        with(readMetadata()) {
            assertEquals("newUserId", user_id)
            assertEquals(listOf("payer"), personas)
        }
        assertEquals(listOf(METADATA_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `each write samples the current metadata`() {
        val observed = mutableListOf<String>()
        metadataProvider = {
            fullyPopulatedMetadata.copy(userId = "userId${observed.size}").also { observed.add(it.userId ?: "") }
        }

        write()
        writer.write()
        writer.write()

        assertEquals(listOf("userId0", "userId1", "userId2"), observed)
        assertEquals("userId2", readMetadata().user_id)
    }

    @Test
    fun `every mutable resource field is persisted`() {
        write()

        val resource = checkNotNull(readMetadata().resource)
        assertEquals(fullyPopulatedMutableResourceProto, resource)
        assertEquals(true, resource.jailbroken)
        assertEquals(true, resource.uses_emmc_storage)
        assertEquals("screenResolution", resource.screen_resolution)
        assertEquals("hostedSdkVersion", resource.hosted_sdk_version)
        assertEquals(
            mapOf("custom.key" to "custom.value", "other.key" to "other.value"),
            resource.extras,
        )
    }

    @Test
    fun `metadata is overwritten when the resource changes`() {
        write()
        resourceProvider = {
            fullyPopulatedResource.copy(screenResolution = "1440x3120", jailbroken = false)
        }
        assertTrue(writer.write())

        with(checkNotNull(readMetadata().resource)) {
            assertEquals("1440x3120", screen_resolution)
            assertEquals(false, jailbroken)
        }
        assertEquals("userId", readMetadata().user_id)
        assertEquals(listOf(METADATA_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is written when no session part is active`() {
        assertFalse(write(directory = null))
        assertFalse(metadataFile().exists())
        assertNoInternalErrors()
    }

    @Test
    fun `user info changes are ignored once the session part is no longer active`() {
        write()
        activePart = null
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "newUserId") }
        assertFalse(writer.write())

        assertEquals("userId", readMetadata().user_id)
        assertNoInternalErrors()
    }

    @Test
    fun `empty session ids are supported`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)

        assertTrue(write(directory))
        assertEquals(fullyPopulatedMetadataProto, readMetadata(directory))
    }

    @Test
    fun `each session part directory gets its own metadata`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)

        assertTrue(write())
        metadataProvider = { fullyPopulatedMetadata.copy(userId = "otherUserId") }
        assertTrue(write(other))

        assertEquals("userId", readMetadata().user_id)
        assertEquals("otherUserId", readMetadata(other).user_id)
        assertNoInternalErrors()
    }

    @Test
    fun `no temporary files are left behind`() {
        write()
        writer.write()
        assertEquals(listOf(METADATA_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `a stale temporary file does not prevent a write`() {
        File(partDir(), "${METADATA_FILE_NAME}1234.tmp").writeText("torn write")
        assertTrue(write())
        assertEquals(fullyPopulatedMetadataProto, readMetadata())
        assertNoInternalErrors()
    }

    @Test
    fun `missing session part directory is reported and does not throw`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        assertFalse(write(absent))
        assertFalse(partDir(absent).exists())
        assertWriteFailureTracked()
    }

    @Test
    fun `a file occupying the session part path is reported and left untouched`() {
        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        val occupyingFile = partDir(occupied).apply { writeText("not a directory") }

        assertFalse(write(occupied))
        assertWriteFailureTracked()
        assertEquals("not a directory", occupyingFile.readText())
    }

    @Test
    fun `oversized metadata is written`() {
        val extras = mapOf("custom.key" to "x".repeat(MAX_PART_FILE_BYTES.toInt() + 1))
        resourceProvider = { fullyPopulatedResource.copy(extras = extras) }

        assertTrue(writer.write())
        assertTrue(metadataFile().length() > MAX_PART_FILE_BYTES)
        assertEquals(extras, readMetadata().resource?.extras)
        assertNoInternalErrors()
    }

    @Test
    fun `failure building the metadata leaves no files on disk`() {
        metadataProvider = { error("boom") }

        assertFalse(write())
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a failing session part source is reported and does not throw`() {
        writer = SessionMetadataWriter(
            lazy { sessionsDir },
            { error("boom") },
            { metadataProvider() },
            { resourceProvider() },
            logger,
        )

        assertFalse(writer.write())
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a failed overwrite leaves the previous metadata intact`() {
        assertTrue(write())
        metadataProvider = { error("boom") }
        assertFalse(writer.write())

        assertEquals(fullyPopulatedMetadataProto, readMetadata())
        assertEquals(listOf(METADATA_FILE_NAME), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `concurrent user info changes leave one valid metadata file`() {
        val threadCount = 8
        val writesPerThread = 25
        write()

        val userIds = (0 until threadCount).map { "userId$it" }
        val latch = CountDownLatch(1)
        val threads = userIds.map { userId ->
            Thread {
                latch.await()
                repeat(writesPerThread) {
                    metadataProvider = { fullyPopulatedMetadata.copy(userId = userId) }
                    writer.write()
                }
            }
        }
        threads.forEach(Thread::start)
        latch.countDown()
        threads.forEach(Thread::join)

        assertTrue(readMetadata().user_id in userIds)
        assertEquals(listOf(METADATA_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun metadataFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), METADATA_FILE_NAME)

    private fun readMetadata(directory: SessionPartDirectory = partDirectory): EnvelopeMetadataProto =
        metadataFile(directory).inputStream().use(EnvelopeMetadataProto.ADAPTER::decode)

    private fun write(directory: SessionPartDirectory? = partDirectory): Boolean {
        activePart = directory
        return writer.write()
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionMetadataWriteFail", logger.internalErrorMessages.single().msg)
    }
}
