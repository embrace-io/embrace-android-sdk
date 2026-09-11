package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadata
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SpanProto
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
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
    private val partSpans = mutableMapOf<String, FakeEmbraceSdkSpan>()
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
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
        spanCount = 0
        partSpans.clear()
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
            { EnvelopeMetadata(userId = "user${writeCount++}") },
            currentSessionPartSpan,
            { emptyList() },
            FakeTelemetryService(),
        )
    }

    @Test
    fun `a pending metadata write lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        drain()
        assertEquals("user0", metadataIn(FIRST_PART_ID)?.user_id)
        writer.onMetadataChanged()
        assertEquals("user0", metadataIn(FIRST_PART_ID)?.user_id)

        // next part begins before write completed
        startPart(SECOND_PART_ID)
        drain()

        // the queued write went to the first part. it ran after the second part's own writes, as
        // those are not debounced, hence the higher user id
        assertEquals("user2", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user1", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(3, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `only the last metadata write queued before a boundary lands in the older part`() {
        startPart(FIRST_PART_ID)
        drain()
        repeat(2) { writer.onMetadataChanged() }
        startPart(SECOND_PART_ID)
        drain()

        // the first of the queued writes was superseded, and the one that ran went to the first part
        assertEquals("user2", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user1", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(3, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a metadata write queued before a part ends is flushed ahead of the newer part`() {
        startPart(FIRST_PART_ID)
        drain()
        writer.onMetadataChanged()

        // the orchestrator always ends a part before the next one starts, and ending it flushes
        // the debounced write rather than leaving it armed
        endPart(FIRST_PART_ID)
        startPart(SECOND_PART_ID)
        drain()

        assertEquals("user1", metadataIn(FIRST_PART_ID)?.user_id)
        assertEquals("user2", metadataIn(SECOND_PART_ID)?.user_id)
        assertEquals(3, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change after a boundary targets the new session part`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()

        writer.onMetadataChanged()
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
        writer.onMetadataChanged()
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

        assertEquals("span0", sessionSpanIn(FIRST_PART_ID)?.name)
        assertEquals("span1", sessionSpanIn(SECOND_PART_ID)?.name)
        assertEquals(2, spanCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a user info change after a boundary does not rewrite either session span`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()
        writer.onMetadataChanged()
        drain()

        assertEquals("span0", sessionSpanIn(FIRST_PART_ID)?.name)
        assertEquals("span1", sessionSpanIn(SECOND_PART_ID)?.name)
        assertEquals(2, spanCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span write queued at a part end lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        clock.tick(10000)
        val endedAt = clock.now()
        endPart(FIRST_PART_ID)
        startPart(SECOND_PART_ID)
        drain()

        with(checkNotNull(sessionSpanIn(FIRST_PART_ID))) {
            assertEquals("span0", name)
            assertEquals(endedAt.millisToNanos(), end_time_unix_nano)
        }
        with(checkNotNull(sessionSpanIn(SECOND_PART_ID))) {
            assertEquals("span1", name)
            assertNull(end_time_unix_nano)
        }
        assertEquals(2, spanCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change after a boundary only updates the newer session part`() {
        startPart(FIRST_PART_ID)
        drain()
        startPart(SECOND_PART_ID)
        drain()

        clock.tick(2000)
        sessionSpan.name = "span-refreshed"
        writer.onSpanSnapshotChanged()
        drain()

        assertEquals("span0", sessionSpanIn(FIRST_PART_ID)?.name)
        assertEquals("span-refreshed", sessionSpanIn(SECOND_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a pending session span write for a deleted session part is reported and does not stop the new part`() {
        startPart(FIRST_PART_ID)
        drain()
        clock.tick(10000)
        endPart(FIRST_PART_ID)
        File(sessionsDir, dirFor(FIRST_PART_ID).dirName).deleteRecursively()
        startPart(SECOND_PART_ID)
        drain()

        assertEquals(listOf("CompletedSpansWriteFail"), logger.internalErrorMessages.map { it.msg })
        assertEquals("span1", sessionSpanIn(SECOND_PART_ID)?.name)
    }

    @Test
    fun `a pending resource read lands in the session part it was queued for`() {
        startPart(FIRST_PART_ID)
        startPart(SECOND_PART_ID)
        drain()

        assertEquals("resource0", metadataIn(FIRST_PART_ID)?.resource?.app_version)
        assertEquals("resource1", metadataIn(SECOND_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    private fun startPart(sessionPartId: String) {
        sessionSpan = FakeEmbraceSdkSpan(name = "span${spanCount++}").apply { start(clock.now()) }
        partSpans[sessionPartId] = sessionSpan
        currentSessionPartSpan.sessionPartSpan = sessionSpan
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, sessionPartId)
    }

    private fun endPart(sessionPartId: String) {
        currentSessionPartSpan.endSession(startNewSession = true)
        writer.onSessionPartEnded(sessionPartId)
        sessionSpan = checkNotNull(currentSessionPartSpan.sessionPartSpan)
    }

    private fun drain() = executor.drainWrites()

    private fun sessionPartDirs(): List<SessionPartDirectory> =
        (sessionsDir.list() ?: emptyArray())
            .mapNotNull(SessionPartDirectory::fromDirName)
            .sortedWith(SessionPartDirectory.comparator)

    private fun dirFor(sessionPartId: String): SessionPartDirectory =
        sessionPartDirs().single { it.sessionPartId == sessionPartId }

    private fun metadataIn(sessionPartId: String): SessionMetadata? =
        partFile(sessionPartId, METADATA_FILE_NAME)
            ?.inputStream()
            ?.use(SessionMetadata.ADAPTER::decode)

    /**
     * The session span persisted for [sessionPartId]: logged as a completed span once its part has
     * ended, and held in the span snapshots until then.
     */
    private fun sessionSpanIn(sessionPartId: String): SpanProto? {
        val spanId = partSpans.getValue(sessionPartId).spanId
        return completedSpansIn(sessionPartId).lastOrNull { it.span_id == spanId }
            ?: spanSnapshotsIn(sessionPartId).lastOrNull { it.span_id == spanId }
    }

    private fun completedSpansIn(sessionPartId: String): List<SpanProto> {
        val bytes = partFile(sessionPartId, COMPLETED_SPANS_FILE_NAME)?.readBytes() ?: return emptyList()
        return CompletedSpans.ADAPTER.decode(bytes).spans
    }

    private fun spanSnapshotsIn(sessionPartId: String): List<SpanProto> =
        partFile(sessionPartId, SPAN_SNAPSHOTS_FILE_NAME)
            ?.inputStream()
            ?.use(SpanSnapshots.ADAPTER::decode)
            ?.spans
            .orEmpty()

    private fun partFile(sessionPartId: String, fileName: String): File? =
        File(File(sessionsDir, dirFor(sessionPartId).dirName), fileName).takeIf(File::isFile)

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
