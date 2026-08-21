package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartWriterImplTest {

    private companion object {
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val OTHER_SESSION_PART_ID = "cccccccccccccccccccccccccccccccc"
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
        private val SYMBOLS = mapOf("armeabi-v7a" to "my-symbols")
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger

    private var writeCount = 0
    private val metadataSource = EnvelopeMetadataSource { EnvelopeMetadata(userId = "user${writeCount++}") }

    private var resourceCount = 0
    private val resourceSource = object : EnvelopeResourceSource {
        override fun getEnvelopeResource(): EnvelopeResource =
            EnvelopeResource(appVersion = "resource${resourceCount++}")

        override fun add(key: String, value: String) = Unit
    }

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        writeCount = 0
        resourceCount = 0
    }

    @Test
    fun `a session part start creates a directory holding the metadata`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        val directory = sessionPartDirs().single()
        assertEquals(clock.now(), directory.timestamp)
        assertEquals(USER_SESSION_ID, directory.userSessionId)
        assertEquals(SESSION_PART_ID, directory.sessionPartId)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part gets its own directory`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        val directories = sessionPartDirs()
        assertEquals(listOf(SESSION_PART_ID, OTHER_SESSION_PART_ID), directories.map { it.sessionPartId })
        assertNoInternalErrors()
    }

    @Test
    fun `a queued write targets the session part it was queued for`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        // start another part before the worker has had a chance to create the first directory
        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        val directories = sessionPartDirs()
        assertEquals(listOf(SESSION_PART_ID, OTHER_SESSION_PART_ID), directories.map { it.sessionPartId })
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is written when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, resourceCount)
        assertNoInternalErrors()
    }

    @Test
    fun `metadata is written as soon as a session part starts`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertEquals("user0", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change rewrites the metadata on the worker`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        writer.onUserInfoChanged()
        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)

        drain()
        assertEquals("user1", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(2, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `every user info change rewrites the metadata`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        repeat(4) { writer.onUserInfoChanged() }
        drain()

        assertEquals("user4", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(5, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change is not queued when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onUserInfoChanged()
        assertEquals(0, executor.submitCount)
        assertEquals(0, writeCount)
    }

    @Test
    fun `a user info change before any session part starts writes nothing`() {
        val writer = createWriter()
        writer.onUserInfoChanged()
        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `nothing more is written to a session part once multi file persistence is disabled`() {
        val configService = configService(enabled = true)
        val writer = createWriter(configService = configService)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        configService.persistenceBehavior = createPersistenceBehavior()
        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        drain()
        writer.onUserInfoChanged()
        drain()

        assertEquals(listOf(SESSION_PART_ID), sessionPartDirs().map { it.sessionPartId })
        assertEquals("user0", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a session part directory that cannot be created is reported and nothing is written`() {
        val writer = createWriter(sessionsDir = tempFolder.newFile("not_a_dir"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertEquals(
            listOf("SessionPartDirectoryStoreFail", "SessionManifestWriteFail", "SessionMetadataWriteFail"),
            logger.internalErrorMessages.map { it.msg },
        )

        writer.onUserInfoChanged()
        drain()

        assertEquals(
            listOf(
                "SessionPartDirectoryStoreFail",
                "SessionManifestWriteFail",
                "SessionMetadataWriteFail",
                "SessionMetadataWriteFail",
            ),
            logger.internalErrorMessages.map { it.msg },
        )
        assertEquals(0, writeCount)
    }

    @Test
    fun `a manifest is written as soon as a session part starts`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        val manifest = checkNotNull(manifestIn(SESSION_PART_ID))
        assertEquals(ENVELOPE_VERSION, manifest.envelope_version)
        assertEquals(ENVELOPE_TYPE, manifest.envelope_type)
        assertEquals(USER_SESSION_ID, manifest.user_session_id)
        assertEquals(SESSION_PART_ID, manifest.session_part_id)
        assertEquals("resource0", manifest.resource?.app_version)
        assertEquals(1, resourceCount)
        assertNoInternalErrors()
    }

    @Test
    fun `the manifest carries the native symbol map`() {
        val writer = createWriter(configService = configService(enabled = true, nativeSymbolMap = SYMBOLS))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertEquals(SYMBOLS, manifestIn(SESSION_PART_ID)?.shared_lib_symbol_mapping?.symbols)
        assertNoInternalErrors()
    }

    @Test
    fun `no symbol mapping is written when the SDK has no native symbols`() {
        val writer = createWriter(configService = configService(enabled = true, nativeSymbolMap = null))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertNull(checkNotNull(manifestIn(SESSION_PART_ID)).shared_lib_symbol_mapping)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part gets its own manifest`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(SESSION_PART_ID, manifestIn(SESSION_PART_ID)?.session_part_id)
        assertEquals(OTHER_SESSION_PART_ID, manifestIn(OTHER_SESSION_PART_ID)?.session_part_id)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change leaves the manifest untouched`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        repeat(4) { writer.onUserInfoChanged() }
        drain()

        assertEquals("resource0", manifestIn(SESSION_PART_ID)?.resource?.app_version)
        assertEquals(1, resourceCount)
        assertNoInternalErrors()
    }

    private fun createWriter(
        enabled: Boolean = true,
        configService: FakeConfigService = configService(enabled),
        sessionsDir: File = this.sessionsDir,
    ) = SessionPartWriterImpl(
        lazy { sessionsDir },
        BackgroundWorker(executor),
        configService,
        TestUuidSource(),
        clock,
        logger,
        resourceSource,
        metadataSource,
    )

    private fun configService(
        enabled: Boolean,
        nativeSymbolMap: Map<String, String>? = emptyMap(),
    ) = FakeConfigService(
        nativeSymbolMap = nativeSymbolMap,
        persistenceBehavior = when {
            enabled -> createPersistenceBehavior(remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f))
            else -> createPersistenceBehavior()
        },
    )

    private fun drain() {
        executor.runCurrentlyBlocked()
    }

    /**
     * Drains the session persistence worker and returns the session part directories on disk, in
     * the order they will be delivered.
     */
    private fun sessionPartDirs(): List<SessionPartDirectory> {
        drain()
        return partDirs()
    }

    private fun partDirs(): List<SessionPartDirectory> =
        (sessionsDir.list() ?: emptyArray())
            .mapNotNull(SessionPartDirectory::fromDirName)
            .sortedWith(SessionPartDirectory.comparator)

    private fun metadataIn(sessionPartId: String): EnvelopeMetadataProto? {
        drain()
        return metadataOnDisk(sessionPartId)
    }

    private fun metadataOnDisk(sessionPartId: String): EnvelopeMetadataProto? =
        partFile(sessionPartId, METADATA_FILE_NAME)?.inputStream()?.use(EnvelopeMetadataProto.ADAPTER::decode)

    private fun manifestIn(sessionPartId: String): SessionManifest? {
        drain()
        return partFile(sessionPartId, MANIFEST_FILE_NAME)?.inputStream()?.use(SessionManifest.ADAPTER::decode)
    }

    private fun partFile(sessionPartId: String, fileName: String): File? {
        val directory = partDirs().single { it.sessionPartId == sessionPartId }
        return File(File(sessionsDir, directory.dirName), fileName).takeIf(File::isFile)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
