package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSpan
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        private const val SESSION_SPAN_FILE_NAME = "session_span.pb"
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
    private var onMetadataRead: () -> Unit = {}
    private val metadataSource = EnvelopeMetadataSource {
        onMetadataRead()
        EnvelopeMetadata(userId = "user${writeCount++}")
    }

    private lateinit var sessionSpan: FakeEmbraceSdkSpan
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan

    private var resourceCount = 0
    private val resourceSource = object : EnvelopeResourceSource {
        override fun getEnvelopeResource(): EnvelopeResource =
            EnvelopeResource(appVersion = "resource${resourceCount++}")

        override fun add(key: String, value: String) = Unit

        override fun addChangeListener(listener: (EnvelopeResource) -> Unit) = Unit
    }

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        writeCount = 0
        resourceCount = 0
        onMetadataRead = {}
        sessionSpan = FakeEmbraceSdkSpan(name = "span0").apply { start(clock.now()) }
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { sessionPartSpan = sessionSpan }
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
    fun `queued metadata writes for a session part are coalesced into one write`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        repeat(4) { writer.onUserInfoChanged() }
        drain()

        // the queued writes were superseded before they ran, so only the last one wrote
        assertEquals("user1", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(2, writeCount)
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
            listOf(
                "SessionPartDirectoryStoreFail",
                "SessionManifestWriteFail",
                "SessionMetadataWriteFail",
                "SessionSpanWriteFail",
            ),
            logger.internalErrorMessages.map { it.msg },
        )

        writer.onUserInfoChanged()
        drain()

        assertEquals(
            listOf(
                "SessionPartDirectoryStoreFail",
                "SessionManifestWriteFail",
                "SessionMetadataWriteFail",
                "SessionSpanWriteFail",
                "SessionMetadataWriteFail",
            ),
            logger.internalErrorMessages.map { it.msg },
        )

        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(
            listOf(
                "SessionPartDirectoryStoreFail",
                "SessionManifestWriteFail",
                "SessionMetadataWriteFail",
                "SessionSpanWriteFail",
                "SessionMetadataWriteFail",
                "SessionSpanWriteFail",
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

    @Test
    fun `the session span is written as soon as a session part starts`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        val span = checkNotNull(sessionSpanIn(SESSION_PART_ID)?.span)
        assertEquals("span0", span.name)
        assertEquals(sessionSpan.traceId, span.trace_id)
        assertEquals(sessionSpan.spanId, span.span_id)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part gets its own session span`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(10000)
        sessionSpan.name = "span1"
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        drain()

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertEquals("span1", sessionSpanIn(OTHER_SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is snapshotted before the write is queued`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        // the part ends before the worker drains, so the span captured at the start is the one written
        currentSessionPartSpan.sessionPartSpan = null
        drain()

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `no session span is written when there is no active session span`() {
        currentSessionPartSpan.sessionPartSpan = null
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertNull(sessionSpanIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change leaves the session span untouched`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        sessionSpan.name = "span1"
        repeat(4) { writer.onUserInfoChanged() }
        drain()

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is rewritten with an end time when the session part ends`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertNull(sessionSpanIn(SESSION_PART_ID)?.span?.end_time_unix_nano)
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        val span = checkNotNull(sessionSpanIn(SESSION_PART_ID)?.span)
        assertEquals("span0", span.name)
        assertEquals(sessionSpan.spanId, span.span_id)
        assertEquals(clock.now().millisToNanos(), span.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a periodic write refreshes the session span for the current part`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)

        clock.tick(2000)
        sessionSpan.name = "span1"
        writer.onPeriodicWrite()
        drain()

        assertEquals("span1", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the ended session span is snapshotted before the write is queued`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        currentSessionPartSpan.sessionPartSpan = null
        drain()

        assertEquals(clock.now().millisToNanos(), sessionSpanIn(SESSION_PART_ID)?.span?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a session part that ends before any session part starts writes nothing`() {
        val writer = createWriter()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `an end for a session part other than the current one is ignored`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val submitCount = executor.submitCount

        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(OTHER_SESSION_PART_ID)
        drain()

        assertEquals(submitCount, executor.submitCount)
        assertNull(sessionSpanIn(SESSION_PART_ID)?.span?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a session part end is not written once multi file persistence is disabled`() {
        val configService = configService(enabled = true)
        val writer = createWriter(configService = configService)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val submitCount = executor.submitCount

        configService.persistenceBehavior = createPersistenceBehavior()
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(submitCount, executor.submitCount)
        assertNull(sessionSpanIn(SESSION_PART_ID)?.span?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `repeated periodic writes keep the latest session span`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        repeat(4) { index ->
            clock.tick(2000)
            sessionSpan.name = "span${index + 1}"
            writer.onPeriodicWrite()
            drain()
        }

        assertEquals("span4", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a periodic write before any session part starts is a no-op`() {
        val writer = createWriter()
        writer.onPeriodicWrite()
        drain()

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a periodic write writes nothing when the part started without a session span`() {
        currentSessionPartSpan.sessionPartSpan = null
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        writer.onPeriodicWrite()
        drain()

        assertNull(sessionSpanIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a periodic write is not made once multi file persistence is disabled`() {
        val configService = configService(enabled = true)
        val writer = createWriter(configService = configService)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val submitCount = executor.submitCount

        configService.persistenceBehavior = createPersistenceBehavior()
        clock.tick(2000)
        sessionSpan.name = "span1"
        writer.onPeriodicWrite()
        drain()

        assertEquals(submitCount, executor.submitCount)
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `queued session span writes are coalesced into one write`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        File(sessionsDir, partDirs().single().dirName).deleteRecursively()
        repeat(4) {
            clock.tick(2000)
            writer.onPeriodicWrite()
        }
        drain()

        assertEquals(listOf("SessionSpanWriteFail"), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a coalesced session span write persists the latest snapshot`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        repeat(4) { index ->
            clock.tick(2000)
            sessionSpan.name = "span${index + 1}"
            writer.onPeriodicWrite()
        }
        drain()

        assertEquals("span4", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash persists the queued session span without the worker being drained`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        writer.onCrash()

        assertEquals(clock.now().millisToNanos(), sessionSpanOnDisk(SESSION_PART_ID)?.span?.end_time_unix_nano)
        assertTrue(executor.isShutdown)
        assertNoInternalErrors()
    }

    @Test
    fun `a write that has already started is not cancelled`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        // supersede the metadata write from inside the metadata write itself
        onMetadataRead = {
            onMetadataRead = {}
            writer.onUserInfoChanged()
        }
        drain()

        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)

        // the write queued while the other one ran is still pending, and runs next
        drain()
        assertEquals("user1", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertEquals(2, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash persists the telemetry queued when the session part started`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onCrash()

        assertEquals(listOf(SESSION_PART_ID), partDirs().map(SessionPartDirectory::sessionPartId))
        assertEquals("resource0", manifestOnDisk(SESSION_PART_ID)?.resource?.app_version)
        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertEquals("span0", sessionSpanOnDisk(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `queued writes for different files do not cancel each other`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onUserInfoChanged()
        drain()

        assertEquals("resource0", manifestIn(SESSION_PART_ID)?.resource?.app_version)
        assertEquals("user0", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `no further writes are made once a crash has been handled`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onCrash()
        val submitCount = executor.submitCount

        clock.tick(2000)
        sessionSpan.name = "span1"
        writer.onPeriodicWrite()
        writer.onUserInfoChanged()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)

        // the worker is sealed by the drain, so queueing anything onto it would be silently
        // discarded - the writer has to stop instead, leaving the crash-time state on disk
        assertEquals(submitCount, executor.submitCount)
        assertEquals("span0", sessionSpanOnDisk(SESSION_PART_ID)?.span?.name)
        assertNull(sessionSpanOnDisk(SESSION_PART_ID)?.span?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a session part started after a crash is not written`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onCrash()
        val submitCount = executor.submitCount
        clock.tick(2000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(listOf(SESSION_PART_ID), partDirs().map(SessionPartDirectory::sessionPartId))
        assertEquals(submitCount, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash does not drain the worker when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onCrash()

        assertEquals(emptyList<SessionPartDirectory>(), partDirs())
        assertFalse(executor.isShutdown)
        assertNoInternalErrors()
    }

    private fun endPart() {
        currentSessionPartSpan.endSession(startNewSession = true)
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
        currentSessionPartSpan,
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
        return manifestOnDisk(sessionPartId)
    }

    private fun manifestOnDisk(sessionPartId: String): SessionManifest? =
        partFile(sessionPartId, MANIFEST_FILE_NAME)?.inputStream()?.use(SessionManifest.ADAPTER::decode)

    private fun sessionSpanIn(sessionPartId: String): SessionPartSpan? {
        drain()
        return sessionSpanOnDisk(sessionPartId)
    }

    private fun sessionSpanOnDisk(sessionPartId: String): SessionPartSpan? =
        partFile(sessionPartId, SESSION_SPAN_FILE_NAME)?.inputStream()?.use(SessionPartSpan.ADAPTER::decode)

    private fun partFile(sessionPartId: String, fileName: String): File? {
        val directory = partDirs().single { it.sessionPartId == sessionPartId }
        return File(File(sessionsDir, directory.dirName), fileName).takeIf(File::isFile)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
