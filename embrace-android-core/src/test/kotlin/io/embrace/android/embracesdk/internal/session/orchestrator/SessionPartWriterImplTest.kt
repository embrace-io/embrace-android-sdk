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
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SpanProto
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SessionPartWriterImplTest {

    private companion object {
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val OTHER_SESSION_PART_ID = "cccccccccccccccccccccccccccccccc"
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
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
    private lateinit var telemetryService: FakeTelemetryService

    private var writeCount = 0
    private var onMetadataRead: () -> Unit = {}
    private val metadataSource = EnvelopeMetadataSource {
        onMetadataRead()
        EnvelopeMetadata(userId = "user${writeCount++}")
    }

    private lateinit var sessionSpan: FakeEmbraceSdkSpan
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private var inFlightSpans: List<EmbraceSdkSpan> = emptyList()
    private var onSpanSnapshotsRead: () -> Unit = {}

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
        telemetryService = FakeTelemetryService()
        writeCount = 0
        resourceCount = 0
        onMetadataRead = {}
        inFlightSpans = emptyList()
        onSpanSnapshotsRead = {}
        sessionSpan = FakeEmbraceSdkSpan(name = "span0", type = EmbType.Ux.Session).apply { start(clock.now()) }
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { sessionPartSpan = sessionSpan }
    }

    @Test
    fun `a session part start creates a directory holding the metadata`() {
        val writer = createWriter()
        val startedAt = clock.now()
        writer.onSessionPartStarted(startedAt, USER_SESSION_ID, SESSION_PART_ID)

        val directory = sessionPartDirs().single()
        assertEquals(startedAt, directory.timestamp)
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
        writer.onMetadataChanged()
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

        repeat(4) { writer.onMetadataChanged() }
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
        writer.onMetadataChanged()
        assertEquals(0, executor.submitCount)
        assertEquals(0, writeCount)
    }

    @Test
    fun `a user info change before any session part starts writes nothing`() {
        val writer = createWriter()
        writer.onMetadataChanged()
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
        writer.onMetadataChanged()
        drain()

        assertEquals(listOf(SESSION_PART_ID), sessionPartDirs().map { it.sessionPartId })
        assertEquals("user0", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a session part directory that cannot be created is reported once and nothing is written`() {
        val writer = createWriter(sessionsDir = tempFolder.newFile("not_a_dir"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertInternalErrors("SessionPartDirectoryStoreFail", "SessionManifestWriteFail")

        writer.onMetadataChanged()
        drain()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertInternalErrors("SessionPartDirectoryStoreFail", "SessionManifestWriteFail")
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
        assertEquals(2, resourceCount)
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

        repeat(4) { writer.onMetadataChanged() }
        drain()

        assertEquals("resource0", manifestIn(SESSION_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is written as soon as a session part starts`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        val span = checkNotNull(sessionSpanIn(SESSION_PART_ID))
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

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)
        assertEquals("span1", sessionSpanIn(OTHER_SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the span bound to the part is written even if the current session span is cleared`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        // the part ends before the worker drains, so the span captured at the start is the one written
        currentSessionPartSpan.sessionPartSpan = null
        drain()

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a queued session span write persists the state at the time it runs`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        sessionSpan.name = "span1"
        drain()

        assertEquals("span1", sessionSpanIn(SESSION_PART_ID)?.name)
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
        repeat(4) { writer.onMetadataChanged() }
        drain()

        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is rewritten with an end time when the session part ends`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertNull(sessionSpanIn(SESSION_PART_ID)?.end_time_unix_nano)
        clock.tick(10000)
        val endedAt = clock.now()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        val span = checkNotNull(sessionSpanIn(SESSION_PART_ID))
        assertEquals("span0", span.name)
        assertEquals(sessionSpan.spanId, span.span_id)
        assertEquals(endedAt.millisToNanos(), span.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change refreshes the session span for the current part`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)

        clock.tick(2000)
        sessionSpan.name = "span1"
        writer.onSpanSnapshotChanged()
        drain()

        assertEquals("span1", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `an ended session span is logged as a completed span rather than a snapshot`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertEquals(listOf("span0"), spanSnapshotNamesOnDisk(SESSION_PART_ID))

        clock.tick(10000)
        val endedAt = clock.now()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(listOf("span0"), completedSpanNamesOnDisk(SESSION_PART_ID))
        assertEquals(emptyList<String>(), spanSnapshotNamesOnDisk(SESSION_PART_ID))
        assertEquals(endedAt.millisToNanos(), sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `an in-flight session span is not logged as a completed span`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertEquals(emptyList<String>(), completedSpanNamesOnDisk(SESSION_PART_ID))
        assertNull(sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span of an ended part is not carried over to the next one`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val endedSpanId = sessionSpan.spanId

        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        drain()

        assertEquals(
            emptyList<String?>(),
            completedSpansOnDisk(OTHER_SESSION_PART_ID).map(SpanProto::span_id).filter { it == endedSpanId },
        )
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change that arrives after its session part ended is dropped`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        File(sessionsDir, partDirs().single().dirName).deleteRecursively()
        writer.onSpanSnapshotChanged()
        drain()
        assertNoInternalErrors()
    }

    @Test
    fun `the ended session span is written even if the current session span is cleared`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        clock.tick(10000)
        val endedAt = clock.now()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        currentSessionPartSpan.sessionPartSpan = null
        drain()

        assertEquals(endedAt.millisToNanos(), sessionSpanIn(SESSION_PART_ID)?.end_time_unix_nano)
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
        assertNull(sessionSpanIn(SESSION_PART_ID)?.end_time_unix_nano)
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
        assertNull(sessionSpanIn(SESSION_PART_ID)?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `repeated session span changes keep the latest session span`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        repeat(4) { index ->
            clock.tick(2000)
            sessionSpan.name = "span${index + 1}"
            writer.onSpanSnapshotChanged()
            drain()
        }

        assertEquals("span4", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change before any session part starts is a no-op`() {
        val writer = createWriter()
        writer.onSpanSnapshotChanged()
        drain()

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change writes nothing when the part started without a session span`() {
        currentSessionPartSpan.sessionPartSpan = null
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        writer.onSpanSnapshotChanged()
        drain()

        assertNull(sessionSpanIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a session span change is not written once multi file persistence is disabled`() {
        val configService = configService(enabled = true)
        val writer = createWriter(configService = configService)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val submitCount = executor.submitCount

        configService.persistenceBehavior = createPersistenceBehavior()
        clock.tick(2000)
        sessionSpan.name = "span1"
        writer.onSpanSnapshotChanged()
        drain()

        assertEquals(submitCount, executor.submitCount)
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)
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
            writer.onSpanSnapshotChanged()
        }
        drain()

        assertEquals(listOf("SpanSnapshotsWriteFail"), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a session part whose directory is gone is given up on after the first failed write`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertNoInternalErrors()
        File(sessionsDir, partDirs().single().dirName).deleteRecursively()

        repeat(10) { index ->
            clock.tick(2000)
            sessionSpan.name = "span${index + 1}"
            writer.onSpanSnapshotChanged()
            writer.onMetadataChanged()
            writer.onSpanSnapshotChanged()
            writer.onSpanCompleted(listOf(completedSpan("completed$index")))
            drain()
        }
        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `the next session part is written after the previous one was given up on`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        File(sessionsDir, partDirs().single().dirName).deleteRecursively()

        clock.tick(2000)
        writer.onSpanSnapshotChanged()
        drain()
        assertEquals(1, logger.internalErrorMessages.size)

        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        clock.tick(2000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        drain()

        assertNotNull(sessionSpanIn(OTHER_SESSION_PART_ID))
        assertNotNull(manifestIn(OTHER_SESSION_PART_ID))
        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `a coalesced session span write persists the latest snapshot`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        repeat(4) { index ->
            clock.tick(2000)
            sessionSpan.name = "span${index + 1}"
            writer.onSpanSnapshotChanged()
        }
        drain()

        assertEquals("span4", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash persists the queued session span without the worker being drained`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        clock.tick(10000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID, crashing = true)
        writer.onCrash()

        assertEquals(clock.now().millisToNanos(), sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano)
        assertTrue(executor.isShutdown)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash never announces its writes as complete even if the seal runs before the crash flush`() {
        val events = mutableListOf<String>()
        val writer = createWriter(onWritesComplete = { events.add("writes-complete") })
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(10000)
        endPart()
        val expectedEndTimeNanos = clock.now().millisToNanos()
        writer.onSessionPartEnded(SESSION_PART_ID, crashing = true)
        drain()
        writer.onCrash()

        assertEquals(emptyList<String>(), events)
        assertEquals(expectedEndTimeNanos, sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `a crash flushes the debounced writes before the worker is shut down`() {
        val events = mutableListOf<String>()
        val recordingExecutor = ShutdownRecordingExecutor(executor) { events.add("shutdown") }
        executor.blockingMode = false
        onMetadataRead = { events.add("metadata-write") }

        val writer = createWriter(worker = BackgroundWorker(recordingExecutor))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertEquals(listOf("metadata-write"), events)
        events.clear()

        writer.onMetadataChanged()
        assertEquals(emptyList<String>(), events)

        writer.onCrash()
        assertEquals(listOf("metadata-write", "shutdown"), events)
        assertEquals("user1", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertNoInternalErrors()
    }

    @Test
    fun `a debounced write runs before the part's writes are announced as complete`() {
        var endTimeAtCompletion: Long? = null
        val writer = createWriter(
            onWritesComplete = {
                endTimeAtCompletion = sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano
            },
        )
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(10000)
        endPart()
        val expectedEndTimeNanos = clock.now().millisToNanos()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(expectedEndTimeNanos, endTimeAtCompletion)
        assertNoInternalErrors()
    }

    @Test
    fun `the first write of a session part is not debounced`() {
        executor.blockingMode = false
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertEquals("span0", sessionSpanOnDisk(SESSION_PART_ID)?.name)
        assertEquals(0, executor.scheduledTasksCount())
        assertNoInternalErrors()
    }

    @Test
    fun `a write after the first one waits out its delay`() {
        executor.blockingMode = false
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onMetadataChanged()
        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)

        executor.moveForwardAndRunBlocked(SessionPartWriterImpl.METADATA_WRITE_DELAY_MS)
        assertEquals("user1", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertNoInternalErrors()
    }

    @Test
    fun `each write queue is armed with its own delay`() {
        val delays = mutableListOf<Long>()
        val writer = createWriter(worker = BackgroundWorker(DelayRecordingExecutor(executor, delays)))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        // every write queued when the part starts is the first one for its file
        assertEquals(listOf(0L, 0L), delays)
        delays.clear()

        writer.onMetadataChanged()
        assertEquals(listOf(SessionPartWriterImpl.METADATA_WRITE_DELAY_MS), delays)
        delays.clear()

        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        assertEquals(listOf(SessionPartWriterImpl.SPAN_SNAPSHOT_WRITE_DELAY_MS), delays)
        drain()
        assertNoInternalErrors()
    }

    @Test
    fun `a write racing with the end of a session part is not queued for it`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertEquals(1, writeCount)

        // simulate another thread reporting a change while the session part is finalised
        onSpanSnapshotsRead = {
            onSpanSnapshotsRead = {}
            writer.onMetadataChanged()
        }
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        // the part is no longer current, so the write does not land
        assertEquals(1, writeCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a completed span racing with the end of a session part is carried over`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        onSpanSnapshotsRead = {
            onSpanSnapshotsRead = {}
            writer.onSpanCompleted(listOf(completedSpan("network-request")))
        }
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        assertEquals(listOf("span0"), completedSpanNamesIn(SESSION_PART_ID))

        clock.tick(1000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(listOf("network-request"), completedSpanNamesIn(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a write armed as a session part ends does not run after the part is sealed`() {
        val events = mutableListOf<String>()
        onMetadataRead = { events.add("metadata-write") }
        lateinit var writer: SessionPartWriterImpl

        val hooked = ScheduleHookExecutor(executor) {
            endPart()
            writer.onSessionPartEnded(SESSION_PART_ID)
        }
        writer = createWriter(
            worker = BackgroundWorker(hooked),
            onWritesComplete = { events.add("writes-complete") },
        )
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        events.clear()

        writer.onMetadataChanged()
        drain()

        assertEquals(listOf("writes-complete"), events)
        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans queued behind the seal of their part are held for the next one`() {
        val hooked = SubmitHookExecutor(executor)
        val writer = createWriter(worker = BackgroundWorker(hooked))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        hooked.onSubmit = {
            endPart()
            writer.onSessionPartEnded(SESSION_PART_ID)
        }
        writer.onSpanCompleted(listOf(completedSpan("network-request")))
        drain()
        assertEquals(listOf("span0"), completedSpanNamesOnDisk(SESSION_PART_ID))

        clock.tick(1000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(listOf("network-request"), completedSpanNamesIn(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a write racing with a crash is dropped rather than run on the crashing thread`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        inFlightSpans = listOf(inFlightSpan("network-request"))
        onSpanSnapshotsRead = {
            onSpanSnapshotsRead = {}
            writer.onCrash()
        }
        clock.tick(1000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertTrue(executor.isShutdown)
        assertNull(inFlightSpanNamesOnDisk(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a write that has already started is not cancelled`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        // supersede the metadata write from inside the metadata write itself
        onMetadataRead = {
            onMetadataRead = {}
            writer.onMetadataChanged()
        }
        drainOnce()

        assertEquals("user0", metadataOnDisk(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)

        // the write queued while the other one ran is still pending, and runs next
        drainOnce()
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
        assertEquals("span0", sessionSpanOnDisk(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `queued writes for different files do not cancel each other`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onMetadataChanged()
        drain()

        assertEquals("resource0", manifestIn(SESSION_PART_ID)?.resource?.app_version)
        assertEquals("user0", metadataIn(SESSION_PART_ID)?.user_id)
        assertEquals(1, writeCount)
        assertEquals("span0", sessionSpanIn(SESSION_PART_ID)?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `the in-flight spans are written as soon as a session part starts`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertEquals(listOf("network-request"), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `the in-flight spans are rewritten when the session part ends`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(1000)
        inFlightSpans = listOf(inFlightSpan("view-load"))
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)

        assertEquals(listOf("view-load"), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `an empty span snapshots file is written when nothing is in flight`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertEquals(emptyList<String>(), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a span snapshot change rewrites the in-flight spans`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        inFlightSpans = listOf(inFlightSpan("view-load"))
        writer.onSpanSnapshotChanged()

        assertEquals(listOf("view-load"), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `the in-flight spans are gathered when the write runs rather than when it is queued`() {
        var reads = 0
        onSpanSnapshotsRead = { reads++ }
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertEquals(1, reads)

        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSpanSnapshotChanged()
        assertEquals(1, reads)
        assertEquals(emptyList<String>(), inFlightSpanNamesOnDisk(SESSION_PART_ID))

        drain()
        assertEquals(2, reads)
        assertEquals(listOf("network-request"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a burst of span snapshot changes is coalesced into one write of the latest spans`() {
        var reads = 0
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        onSpanSnapshotsRead = { reads++ }
        repeat(4) { index ->
            clock.tick(1000)
            inFlightSpans = listOf(inFlightSpan("view-load-$index"))
            writer.onSpanSnapshotChanged()
        }
        drain()

        assertEquals(1, reads)
        assertEquals(listOf("view-load-3"), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `the in-flight spans persisted for a session part are the ones it ended with`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(1000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        inFlightSpans = listOf(inFlightSpan("next-part-span"))
        drain()

        assertEquals(listOf("network-request"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `the in-flight spans persisted for a session part are the ones it started with`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        clock.tick(1000)
        inFlightSpans = listOf(inFlightSpan("later-span"))
        drain()

        assertEquals(listOf("network-request"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a span snapshot change is dropped once another session part has started`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        writer.onSpanSnapshotChanged()

        clock.tick(10000)
        inFlightSpans = listOf(inFlightSpan("next-part-span"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        drain()

        assertEquals(listOf("network-request"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
        assertEquals(listOf("next-part-span"), inFlightSpanNamesOnDisk(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `an in-flight span is snapshotted when its write runs rather than when it is queued`() {
        val writer = createWriter()
        val span = inFlightSpan("network-request")
        inFlightSpans = listOf(span)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        span.name = "view-load"
        drain()

        assertEquals(listOf("view-load"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a span snapshot change before any session part starts writes nothing`() {
        val writer = createWriter()
        writer.onSpanSnapshotChanged()

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `a span snapshot change after a session part ended is not written to it`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        clock.tick(1000)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        inFlightSpans = listOf(inFlightSpan("view-load"))
        writer.onSpanSnapshotChanged()

        assertEquals(listOf("network-request"), inFlightSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a span snapshot change is ignored when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onSpanSnapshotChanged()

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `no span snapshots are written once a crash has been handled`() {
        val writer = createWriter()
        inFlightSpans = listOf(inFlightSpan("network-request"))
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onCrash()
        val submitCount = executor.submitCount

        inFlightSpans = listOf(inFlightSpan("view-load"))
        writer.onSpanSnapshotChanged()

        assertEquals(submitCount, executor.submitCount)
        assertEquals(listOf("network-request"), inFlightSpanNamesOnDisk(SESSION_PART_ID))
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
        writer.onSpanSnapshotChanged()
        writer.onMetadataChanged()
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)

        // the worker is sealed by the drain, so queueing anything onto it would be silently
        // discarded - the writer has to stop instead, leaving the crash-time state on disk
        assertEquals(submitCount, executor.submitCount)
        assertEquals("span0", sessionSpanOnDisk(SESSION_PART_ID)?.name)
        assertNull(sessionSpanOnDisk(SESSION_PART_ID)?.end_time_unix_nano)
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

    @Test
    fun `a completed span is appended to the log`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        writer.onSpanCompleted(listOf(completedSpan("network-request")))

        assertEquals(listOf("network-request"), completedSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans are appended in the order they are reported`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        writer.onSpanCompleted(listOf(completedSpan("first"), completedSpan("second")))
        writer.onSpanCompleted(listOf(completedSpan("third")))

        assertEquals(listOf("first", "second", "third"), completedSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a span completing does not supersede the spans already appended`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        // both appends are queued before either runs. assert a coalescing queue is not used
        writer.onSpanCompleted(listOf(completedSpan("first")))
        writer.onSpanCompleted(listOf(completedSpan("second")))
        drain()

        assertEquals(listOf("first", "second"), completedSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `a session part in which nothing completes writes no log`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertNull(partFile(SESSION_PART_ID, COMPLETED_SPANS_FILE_NAME))
        assertNoInternalErrors()
    }

    @Test
    fun `an empty batch of completed spans queues no write`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        val submitCount = executor.submitCount

        writer.onSpanCompleted(emptyList())

        assertEquals(submitCount, executor.submitCount)
        assertNull(partFile(SESSION_PART_ID, COMPLETED_SPANS_FILE_NAME))
        assertNoInternalErrors()
    }

    @Test
    fun `each session part logs the spans that completed while it was active`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onSpanCompleted(listOf(completedSpan("first")))

        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        writer.onSpanCompleted(listOf(completedSpan("second")))

        assertEquals(listOf("first"), completedSpanNamesIn(SESSION_PART_ID))
        assertEquals(listOf("second"), completedSpanNamesIn(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans before any session part starts are held and logged against the first part`() {
        val writer = createWriter()
        writer.onSpanCompleted(listOf(completedSpan("network-request")))
        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)

        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        assertEquals(listOf("network-request"), completedSpanNamesIn(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans after a session part ended are logged against the next part`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        drain()

        writer.onSpanCompleted(listOf(completedSpan("carried-over")))
        drain()
        assertEquals(listOf("span0"), completedSpanNamesOnDisk(SESSION_PART_ID))

        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(listOf("carried-over"), completedSpanNamesIn(OTHER_SESSION_PART_ID))
        assertEquals(listOf("span0"), completedSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans while a session part is open are logged against it rather than held`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        writer.onSpanCompleted(listOf(completedSpan("network-request")))
        drain()
        assertEquals(listOf("network-request"), completedSpanNamesOnDisk(SESSION_PART_ID))

        clock.tick(10000)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)

        assertEquals(emptyList<String?>(), completedSpanNamesIn(OTHER_SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `spans held past the limit are dropped and counted as an applied limit`() {
        val writer = createWriter()
        writer.onSpanCompleted(List(1002) { completedSpan("held-$it") })

        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)

        assertEquals(1000, completedSpanNamesIn(SESSION_PART_ID).size)
        assertEquals(
            List(2) { "carried_over_span" to AppliedLimitType.DROP },
            telemetryService.appliedLimits,
        )
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans are ignored when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onSpanCompleted(listOf(completedSpan("network-request")))

        assertEquals(emptyList<SessionPartDirectory>(), sessionPartDirs())
        assertEquals(0, executor.submitCount)
        assertNoInternalErrors()
    }

    @Test
    fun `no completed spans are appended once a crash has been handled`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        writer.onSpanCompleted(listOf(completedSpan("first")))
        writer.onCrash()
        val submitCount = executor.submitCount

        writer.onSpanCompleted(listOf(completedSpan("second")))

        assertEquals(submitCount, executor.submitCount)
        assertEquals(listOf("first"), completedSpanNamesOnDisk(SESSION_PART_ID))
        assertNoInternalErrors()
    }

    @Test
    fun `the session span retains its data until the part's writes have completed`() {
        val writer = createWriter()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()
        assertTrue(sessionSpan.dataRetainedAfterStop)
        assertFalse(sessionSpan.retainedDataReleased)

        endPart()
        writer.onSessionPartEnded(SESSION_PART_ID)
        assertFalse(sessionSpan.retainedDataReleased)

        drain()
        assertTrue(sessionSpan.retainedDataReleased)
        assertNoInternalErrors()
    }

    @Test
    fun `the session span is not asked to retain its data when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        drain()

        assertFalse(sessionSpan.dataRetainedAfterStop)
        assertNoInternalErrors()
    }

    /**
     * Records the delay that each write is armed with.
     */
    private class DelayRecordingExecutor(
        private val delegate: ScheduledExecutorService,
        private val delays: MutableList<Long>,
    ) : ScheduledExecutorService by delegate {

        override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            delays.add(delay)
            return delegate.schedule(command, delay, unit)
        }
    }

    private class ScheduleHookExecutor(
        private val delegate: ScheduledExecutorService,
        private val onFirstSchedule: () -> Unit,
    ) : ScheduledExecutorService by delegate {

        private var hooked = false

        override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            if (!hooked && delay > 0) {
                hooked = true
                onFirstSchedule()
            }
            return delegate.schedule(command, delay, unit)
        }
    }

    private class SubmitHookExecutor(
        private val delegate: ScheduledExecutorService,
    ) : ScheduledExecutorService by delegate {

        var onSubmit: () -> Unit = {}

        override fun submit(task: Runnable?): Future<*> {
            val hook = onSubmit
            onSubmit = {}
            hook()
            return delegate.submit(task)
        }
    }

    private class ShutdownRecordingExecutor(
        private val delegate: ScheduledExecutorService,
        private val onShutdown: () -> Unit,
    ) : ScheduledExecutorService by delegate {

        override fun shutdown() {
            onShutdown()
            delegate.shutdown()
        }
    }

    private fun inFlightSpan(name: String) =
        FakeEmbraceSdkSpan(name = name).apply { start(clock.now()) }

    private fun completedSpan(name: String) = Span(
        traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
        spanId = "aaaaaaaaaaaaaaa2",
        name = name,
        startTimeNanos = clock.now().millisToNanos(),
        endTimeNanos = (clock.now() + 1000).millisToNanos(),
    )

    private fun endPart() {
        currentSessionPartSpan.endSession(startNewSession = true)
    }

    private fun createWriter(
        enabled: Boolean = true,
        configService: FakeConfigService = configService(enabled),
        sessionsDir: File = this.sessionsDir,
        worker: BackgroundWorker = BackgroundWorker(executor),
        onWritesComplete: () -> Unit = {},
    ) = SessionPartWriterImpl(
        lazy { sessionsDir },
        worker,
        configService,
        TestUuidSource(),
        clock,
        logger,
        resourceSource,
        metadataSource,
        currentSessionPartSpan,
        {
            onSpanSnapshotsRead()
            inFlightSpans
        },
        telemetryService,
        onWritesComplete = onWritesComplete,
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

    private fun drain() = executor.drainWrites()

    private fun drainOnce() {
        executor.moveForwardAndRunBlocked(WRITE_DRAIN_TICK_MS)
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

    private fun sessionSpanIn(sessionPartId: String): SpanProto? {
        drain()
        return sessionSpanOnDisk(sessionPartId)
    }

    /**
     * The session span persisted for [sessionPartId]: logged as a completed span once its part has
     * ended, and held in the span snapshots until then.
     */
    private fun sessionSpanOnDisk(sessionPartId: String): SpanProto? =
        completedSpansOnDisk(sessionPartId).lastOrNull(::isSessionSpan)
            ?: spanSnapshotsOnDisk(sessionPartId).lastOrNull(::isSessionSpan)

    private fun isSessionSpan(span: SpanProto): Boolean =
        span.attributes.any { it.key == "emb.type" && it.value_ == "ux.session" }

    private fun inFlightSpanNamesIn(sessionPartId: String): List<String?>? {
        drain()
        return inFlightSpanNamesOnDisk(sessionPartId)
    }

    /**
     * The names of the spans snapshotted for a session part, other than its session span.
     */
    private fun inFlightSpanNamesOnDisk(sessionPartId: String): List<String?>? =
        spanSnapshotNamesOnDisk(sessionPartId, includeSessionSpan = false)

    private fun spanSnapshotNamesOnDisk(
        sessionPartId: String,
        includeSessionSpan: Boolean = true,
    ): List<String?>? =
        spanSnapshotsOnDisk(sessionPartId)
            .takeIf { partFile(sessionPartId, SPAN_SNAPSHOTS_FILE_NAME) != null }
            ?.filter { includeSessionSpan || !isSessionSpan(it) }
            ?.map { it.name }

    private fun spanSnapshotsOnDisk(sessionPartId: String): List<SpanProto> =
        partFile(sessionPartId, SPAN_SNAPSHOTS_FILE_NAME)
            ?.inputStream()
            ?.use(SpanSnapshots.ADAPTER::decode)
            ?.spans
            .orEmpty()

    private fun completedSpanNamesIn(sessionPartId: String): List<String?> {
        drain()
        return completedSpanNamesOnDisk(sessionPartId)
    }

    private fun completedSpanNamesOnDisk(sessionPartId: String): List<String?> =
        completedSpansOnDisk(sessionPartId).map { it.name }

    private fun completedSpansOnDisk(sessionPartId: String): List<SpanProto> {
        val bytes = partFile(sessionPartId, COMPLETED_SPANS_FILE_NAME)?.readBytes() ?: return emptyList()
        return CompletedSpans.ADAPTER.decode(bytes).spans
    }

    private fun partFile(sessionPartId: String, fileName: String): File? {
        val directory = partDirs().single { it.sessionPartId == sessionPartId }
        return File(File(sessionsDir, directory.dirName), fileName).takeIf(File::isFile)
    }

    private fun assertInternalErrors(vararg expected: String) {
        assertEquals(expected.sorted(), logger.internalErrorMessages.map { it.msg }.sorted())
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
