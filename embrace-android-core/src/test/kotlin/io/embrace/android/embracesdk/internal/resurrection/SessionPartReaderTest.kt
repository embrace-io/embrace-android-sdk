package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeResult
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SessionPartReaderTest {

    private companion object {
        private const val PROCESS_ID = "cccccccccccccccccccccccccccccccc"
        private const val PERSISTED_PROCESS_ID = "dddddddddddddddddddddddddddddddd"
        private const val METADATA_FILE_NAME = "metadata.pb"

        private val partDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )

        private val laterPartDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME + 1000,
            uuid = "d2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
        )

        private fun sessionSpan(processIdentifier: String? = PERSISTED_PROCESS_ID) = Span(
            traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
            spanId = "aaaaaaaaaaaaaaa1",
            name = "emb-session",
            startTimeNanos = 1726739283136000000L,
            endTimeNanos = 1726739284136000000L,
            status = Span.Status.UNSET,
            events = emptyList(),
            attributes = listOfNotNull(
                Attribute(key = "emb.type", data = "ux.session"),
                processIdentifier?.let { Attribute(key = EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, data = it) },
            ),
            links = emptyList(),
        )

        private fun ids(vararg directories: SessionPartDirectory): List<String> =
            directories.map(SessionPartDirectory::sessionPartId)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var directoryStore: SessionPartDirectoryStore
    private lateinit var intakeService: FakeIntakeService
    private lateinit var slowIntake: SlowIntakeService
    private lateinit var writeTracker: SessionPartWriteTracker

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        directoryStore = SessionPartDirectoryStore(lazy { sessionsDir }, BackgroundWorker(executor), clock, logger)
        intakeService = FakeIntakeService()
        slowIntake = SlowIntakeService()
        writeTracker = SessionPartWriteTracker()
    }

    @Test
    fun `a persisted session part is delivered to the intake service and deleted`() {
        persist(partDirectory)
        createReader().readPersistedSessionParts()

        val intake = intakeService.getIntakes<SessionPartPayload>().single()
        assertEquals(sessionSpan(), intake.envelope.getSessionPartSpan())
        assertEquals(SESSION_ENVELOPE_VERSION, intake.envelope.version)
        assertEquals(SESSION_ENVELOPE_TYPE, intake.envelope.type)

        with(intake.metadata) {
            assertEquals(partDirectory.timestamp, timestamp)
            assertEquals(partDirectory.uuid, uuid)
            assertEquals(partDirectory.userSessionId, userSessionId)
            assertEquals(partDirectory.sessionPartId, sessionPartId)
            assertEquals(SupportedEnvelopeType.SESSION, envelopeType)
            assertEquals(PayloadType.SESSION, payloadType)
            assertTrue(complete)
        }
        assertDeleted(partDirectory)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a resurrected session part is taken in as incomplete`() {
        persist(partDirectory)
        createReader().readPersistedSessionParts(performingResurrection = true)

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertFalse(intakeService.cacheList.single().metadata.complete)
        assertDeleted(partDirectory)
    }

    @Test
    fun `the process that recorded the session part is preserved`() {
        persist(partDirectory)
        createReader().readPersistedSessionParts()
        assertEquals(PERSISTED_PROCESS_ID, intakeService.intakeList.single().metadata.processIdentifier)
    }

    @Test
    fun `the current process is used when the session part does not record one`() {
        persist(partDirectory, span = sessionSpan(processIdentifier = null))
        createReader().readPersistedSessionParts()
        assertEquals(PROCESS_ID, intakeService.intakeList.single().metadata.processIdentifier)
    }

    @Test
    fun `session parts are taken in serially in time order`() {
        persistTwoParts()
        slowIntake.stall = false
        createReader(slowIntake).readPersistedSessionParts()

        assertEquals(
            listOf(
                "take:${partDirectory.sessionPartId}",
                "wait:${partDirectory.sessionPartId}",
                "take:${laterPartDirectory.sessionPartId}",
                "wait:${laterPartDirectory.sessionPartId}",
            ),
            slowIntake.events,
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part that is still being written to is left alone`() {
        persist(partDirectory)
        writeTracker.markWriting(partDirectory)

        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    @Test
    fun `a session part that cannot be reconstructed is deleted without being delivered or stopping the pass`() {
        persistTwoParts()
        assertTrue(File(File(sessionsDir, partDirectory.dirName), METADATA_FILE_NAME).delete())
        createReader().readPersistedSessionParts()

        assertEquals(ids(laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part that intake does not store is left on disk and the intake pass continues`() {
        persistTwoParts()
        intakeService.pendingResults.addAll(listOf(IntakeResult.RETRYABLE_FAILURE, IntakeResult.PERMANENT_FAILURE))

        createReader().readPersistedSessionParts()

        // neither part was stored, so the only copy of each one's telemetry is retained
        assertEquals(ids(partDirectory, laterPartDirectory), intakesAttempted())
        assertRetained(partDirectory, laterPartDirectory)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `reading persisted session parts is a no-op when there is nothing on disk`() {
        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = emptyList())
    }

    @Test
    fun `everything on disk is deleted when multi file persistence is disabled`() {
        persistTwoParts()
        assertTrue(File(sessionsDir, "unparseable.tmp").createNewFile())
        createReader(enabled = false).readPersistedSessionParts()

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertFalse(sessionsDir.exists())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a session part whose intake times out is kept and stops the intake pass`() {
        persistTwoParts()
        createReader(slowIntake).readPersistedSessionParts()

        assertEquals(ids(partDirectory), slowIntake.takenPartIds)
        assertRetained(partDirectory, laterPartDirectory)
        assertTrue(logger.internalErrorMessages.single().throwable is TimeoutException)
    }

    @Test
    fun `a session part left by a timed out pass is retried when there is a new reader instance`() {
        persistTwoParts()
        createReader(slowIntake).readPersistedSessionParts()
        slowIntake.stall = false
        createReader(slowIntake).readPersistedSessionParts()

        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertEquals(1, slowIntake.takes.first().waits)
        assertDeleted(partDirectory, laterPartDirectory)
    }

    /**
     * An intake service that can be configured to behave in nonstandard ways:
     *
     * - Set [stall] to true to ensure an intake never completes. Setting it to false completes the intakes right away.
     *
     * Every take and wait is recorded in [events].
     */
    private class SlowIntakeService : IntakeService {
        val takes: MutableList<SlowTake> = mutableListOf()
        val events: MutableList<String> = mutableListOf()
        var stall: Boolean = true

        val takenPartIds: List<String>
            get() = takes.map(SlowTake::partId)

        override fun shutdown() {}

        override fun take(
            intake: Envelope<*>,
            metadata: StoredTelemetryMetadata,
            staleEntry: StoredTelemetryMetadata?,
            onStored: (() -> Unit)?,
        ): Future<IntakeResult> {
            val take = SlowTake(metadata.sessionPartId, events, onStored)
            takes.add(take)
            events.add("take:${take.partId}")
            if (!stall) {
                take.store()
            }
            return take
        }
    }

    private class SlowTake(
        val partId: String,
        private val events: MutableList<String>,
        private val onStored: (() -> Unit)?,
    ) : Future<IntakeResult> {
        var waits: Int = 0
        private var done = false

        fun store() {
            onStored?.invoke()
            done = true
        }

        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = done
        override fun get() = error("the reader must always wait with a timeout")
        override fun get(timeout: Long, unit: TimeUnit): IntakeResult {
            waits++
            events.add("wait:$partId")
            if (!done) {
                throw TimeoutException("stalled")
            }
            return IntakeResult.STORED
        }
    }

    private fun createReader(
        intakeService: IntakeService = this.intakeService,
        enabled: Boolean = true,
    ) = SessionPartReader(
        sessionsDir = lazy { sessionsDir },
        directoryStore = directoryStore,
        reconstructionService = SessionReconstructionService(lazy { sessionsDir }, logger),
        intakeService = intakeService,
        writeTracker = writeTracker,
        processIdProvider = { PROCESS_ID },
        configService = FakeConfigService(
            config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { enabled }) }),
        ),
        logger = logger,
    )

    /**
     * Persists [partDirectory] and [laterPartDirectory], the later one first so that the reader has to
     * order them.
     */
    private fun persistTwoParts() {
        persist(laterPartDirectory)
        persist(partDirectory)
    }

    private fun persist(directory: SessionPartDirectory, span: Span = sessionSpan()) {
        create(directory)

        val target = SessionPartWriteTarget(lazy { sessionsDir }) { directory }
        SessionMetadataWriter(
            target = target,
            metadataSource = { EnvelopeMetadata(username = "fake-user") },
            resourceSource = { EnvelopeResource(appVersion = "1.0.0") },
            envelopeVersion = SESSION_ENVELOPE_VERSION,
            envelopeType = SESSION_ENVELOPE_TYPE,
            sharedLibSymbolMappingSource = { null },
            logger = logger,
        ).write()
        CompletedSpansWriter(target, logger).write(listOf(span))
    }

    /**
     * Creates the directory via the store, then drains the worker so the work has completed.
     */
    private fun create(directory: SessionPartDirectory) {
        directoryStore.create(directory)
        executor.runCurrentlyBlocked()
    }

    private fun intakesAttempted(): List<String> = intakeService.intakeList.map { it.metadata.sessionPartId }

    /**
     * Exactly [directories] are still stored, both in the store's index and on disk.
     */
    private fun assertRetained(vararg directories: SessionPartDirectory) {
        assertEquals(directories.toSet(), directoryStore.storedDirectories().toSet())
        assertEquals(directories.map(SessionPartDirectory::dirName).toSet(), sessionsDir.list()?.toSet() ?: emptySet<String>())
    }

    private fun assertDeleted(vararg directories: SessionPartDirectory) {
        val stored = directoryStore.storedDirectories().toSet()
        val onDisk = sessionsDir.list()?.toSet() ?: emptySet<String>()
        directories.forEach { directory ->
            assertTrue(directory !in stored)
            assertTrue(directory.dirName !in onDisk)
        }
    }

    /**
     * Nothing reached intake, and every session part is still on disk: nothing was delivered, so
     * nothing may have been deleted.
     */
    private fun assertNothingDelivered(retained: List<SessionPartDirectory>) {
        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertRetained(*retained.toTypedArray())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
