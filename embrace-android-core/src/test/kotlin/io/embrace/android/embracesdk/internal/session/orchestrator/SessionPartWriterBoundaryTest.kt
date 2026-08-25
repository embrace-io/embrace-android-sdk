package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
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
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSpan
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers what happens to session part telemetry that is queued for writing before a session part
 * ends, but which executes after the next session part has begun.
 */
internal class SessionPartWriterBoundaryTest {

    private companion object {
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val FIRST_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val SECOND_PART_ID = "cccccccccccccccccccccccccccccccc"
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val SESSION_SPAN_FILE_NAME = "session_span.pb"
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var writer: SessionPartWriter
    private var writeCount = 0
    private var resourceCount = 0
    private var spanCount = 0
    private lateinit var sessionSpan: FakeEmbraceSdkSpan
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
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
        spanCount = 0
        sessionSpan = FakeEmbraceSdkSpan().apply { start(clock.now()) }
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { sessionPartSpan = sessionSpan }
        writer = SessionPartWriterImpl(
            lazy { sessionsDir },
            BackgroundWorker(executor),
            FakeConfigService(
                persistenceBehavior = createPersistenceBehavior(
                    remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
                ),
            ),
            TestUuidSource(),
            clock,
            logger,
            resourceSource,
            EnvelopeMetadataSource { EnvelopeMetadata(userId = "user${writeCount++}") },
            currentSessionPartSpan,
        )
    }

    @Test
    fun `a pending metadata write lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        drain()
        assertEquals("user0", metadataIn(FIRST_PART_ID)?.user_id)
        writer.onUserInfoChanged()
        assertEquals("user0", metadataIn(FIRST_PART_ID)?.user_id)

        // next part begins before write completed
        startPart(SECOND_PART_ID)
        drain()

        // the queued write went to the first part
        assertEquals("user1", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user2", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(3, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `several queued writes spanning a boundary all land in the session part they were queued for`() {
        startPart(FIRST_PART_ID)
        drain()
        repeat(2) { writer.onUserInfoChanged() }
        startPart(SECOND_PART_ID)
        drain()

        // both queued writes went to the first part
        assertEquals("user2", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user3", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(4, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change after a boundary targets the new session part`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()

        writer.onUserInfoChanged()
        drain()

        assertEquals("user0", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user2", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(3, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a pending metadata write for a deleted session part is reported and does not stop the new part`() {
        startPart(FIRST_PART_ID)
        drain()
        writer.onUserInfoChanged()
        File(sessionsDir, dirFor(FIRST_PART_ID).dirName).deleteRecursively()

        startPart(SECOND_PART_ID)
        drain()
        assertEquals(listOf("SessionMetadataWriteFail"), logger.internalErrorMessages.map { it.msg })
        assertEquals("user1", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(2, writeCount)
    }

    @Test
    fun `a pending session span write lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        startPart(SECOND_PART_ID)
        drain()

        assertEquals("span0", sessionSpanIn(FIRST_PART_ID)?.span?.name)
        assertEquals("span1", sessionSpanIn(SECOND_PART_ID)?.span?.name)
        assertEquals(2, spanCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change after a boundary does not rewrite either session span`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()
        writer.onUserInfoChanged()
        drain()

        assertEquals("span0", sessionSpanIn(FIRST_PART_ID)?.span?.name)
        assertEquals("span1", sessionSpanIn(SECOND_PART_ID)?.span?.name)
        assertEquals(2, spanCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a pending manifest write lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        startPart(SECOND_PART_ID)
        drain()

        assertEquals("resource0", manifestIn(FIRST_PART_ID)?.resource?.app_version)
        assertEquals("resource1", manifestIn(SECOND_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change after a boundary does not rewrite either manifest`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()

        writer.onUserInfoChanged()
        drain()

        assertEquals("resource0", manifestIn(FIRST_PART_ID)?.resource?.app_version)
        assertEquals("resource1", manifestIn(SECOND_PART_ID)?.resource?.app_version)
        assertEquals(2, resourceCount)
        assertNoInternalErrors()
    }

    private fun startPart(sessionPartId: String) {
        sessionSpan.name = "span${spanCount++}"
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, sessionPartId)
    }

    private fun drain() {
        executor.runCurrentlyBlocked()
    }

    private fun sessionPartDirs(): List<SessionPartDirectory> =
        (sessionsDir.list() ?: emptyArray())
            .mapNotNull(SessionPartDirectory::fromDirName)
            .sortedWith(SessionPartDirectory.comparator)

    private fun dirFor(sessionPartId: String): SessionPartDirectory =
        sessionPartDirs().single { it.sessionPartId == sessionPartId }

    private fun metadataIn(sessionPartId: String): EnvelopeMetadataProto? =
        partFile(sessionPartId, METADATA_FILE_NAME)
            ?.inputStream()
            ?.use(EnvelopeMetadataProto.ADAPTER::decode)

    private fun sessionSpanIn(sessionPartId: String): SessionPartSpan? =
        partFile(sessionPartId, SESSION_SPAN_FILE_NAME)
            ?.inputStream()
            ?.use(SessionPartSpan.ADAPTER::decode)

    private fun manifestIn(sessionPartId: String): SessionManifest? =
        partFile(sessionPartId, MANIFEST_FILE_NAME)
            ?.inputStream()
            ?.use(SessionManifest.ADAPTER::decode)

    private fun partFile(sessionPartId: String, fileName: String): File? =
        File(File(sessionsDir, dirFor(sessionPartId).dirName), fileName).takeIf(File::isFile)

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
