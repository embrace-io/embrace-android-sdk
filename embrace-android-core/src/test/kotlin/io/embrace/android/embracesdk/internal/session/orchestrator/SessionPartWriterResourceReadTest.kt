package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.session.persistence.SpanProto
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartWriterResourceReadTest {

    private companion object {
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private val SYMBOLS = mapOf("armeabi-v7a" to "my-symbols")

        private val RESOURCE = EnvelopeResource(
            appVersion = "1.2.3",
            appEcosystemId = "com.example.app",
            sdkVersion = "7.0.0",
        )

        private val spanTemplate = SpanProto(
            trace_id = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
            span_id = "aaaaaaaaaaaaaaa1",
            name = "emb-session",
            start_time_unix_nano = 1726739283136000000L,
            end_time_unix_nano = 1726739284136000000L,
        )

        private val completedSpan = spanTemplate.copy(
            span_id = "aaaaaaaaaaaaaaa2",
            name = "emb-startup-moment",
        )

        private val snapshotSpan = spanTemplate.copy(
            span_id = "aaaaaaaaaaaaaaa3",
            name = "emb-network-request",
            end_time_unix_nano = null,
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var writer: SessionPartWriter
    private lateinit var sessionSpan: FakeEmbraceSdkSpan
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private lateinit var service: SessionReconstructionService

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        sessionSpan = FakeEmbraceSdkSpan(name = "emb-session").apply { start(clock.now()) }
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { sessionPartSpan = sessionSpan }
        writer = SessionPartWriterImpl(
            lazy { sessionsDir },
            BackgroundWorker(executor),
            FakeConfigService(
                nativeSymbolMap = SYMBOLS,
                persistenceBehavior = createPersistenceBehavior(
                    remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
                ),
            ),
            TestUuidSource(),
            clock,
            logger,
            FakeEnvelopeResourceSource().apply { resource = RESOURCE },
            EnvelopeMetadataSource { EnvelopeMetadata(userId = "my-user-id") },
            currentSessionPartSpan,
        )
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
    }

    @Test
    fun `a session part written to disk can be reconstructed`() {
        val envelope = checkNotNull(writeSessionPart())
        assertEquals("0.1.0", envelope.version)
        assertEquals("spans", envelope.type)
        assertEquals(RESOURCE, envelope.resource)
        assertEquals("my-user-id", envelope.metadata?.userId)
        assertEquals(SYMBOLS, envelope.data.sharedLibSymbolMapping)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `the session span written at the start of the part is reconstructed as a snapshot`() {
        val envelope = checkNotNull(writeSessionPart())
        val expected = checkNotNull(sessionSpan.snapshot()).copy(events = null, links = null)
        assertEquals(expected, envelope.data.spanSnapshots?.last())
        assertNull(envelope.data.spans?.find { it.spanId == sessionSpan.spanId })
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `the session span written when the part ends is reconstructed as a completed span`() {
        writeSessionPart()
        clock.tick(1000)
        endSessionPart()

        val envelope = checkNotNull(service.reconstruct(directory()))
        val expected = checkNotNull(sessionSpan.snapshot()).copy(events = null, links = null)
        assertEquals(expected, envelope.data.spans?.last())
        assertNull(envelope.data.spanSnapshots?.find { it.spanId == sessionSpan.spanId })
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a session part cannot be reconstructed without the manifest`() {
        writeSessionPart()
        File(partDir(), MANIFEST_FILE_NAME).delete()
        assertNull(service.reconstruct(directory()))
        assertEquals(listOf("SessionReconstructionFail"), logger.internalErrorMessages.map { it.msg })
    }

    /**
     * Starts a session part, then hand-writes the files that no production writer produces yet, so
     * the directory holds everything reconstruction requires.
     */
    private fun writeSessionPart() = run {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        executor.runCurrentlyBlocked()

        writePartFile(
            COMPLETED_SPANS_FILE_NAME,
            Buffer().apply {
                write(CompletedSpans.ADAPTER.encode(CompletedSpans(spans = listOf(completedSpan))))
            }.readByteArray(),
        )
        writePartFile(
            SPAN_SNAPSHOTS_FILE_NAME,
            SpanSnapshots.ADAPTER.encode(
                SpanSnapshots(format_version = FORMAT_VERSION, spans = listOf(snapshotSpan)),
            ),
        )
        service.reconstruct(directory())
    }

    private fun endSessionPart() {
        currentSessionPartSpan.endSession(startNewSession = true)
        writer.onSessionPartEnded(SESSION_PART_ID)
        executor.runCurrentlyBlocked()
    }

    private fun writePartFile(fileName: String, bytes: ByteArray) {
        File(partDir(), fileName).writeBytes(bytes)
    }

    private fun partDir(): File = File(sessionsDir, directory().dirName)

    private fun directory(): SessionPartDirectory =
        (sessionsDir.list() ?: emptyArray()).mapNotNull(SessionPartDirectory::fromDirName).single()
}
