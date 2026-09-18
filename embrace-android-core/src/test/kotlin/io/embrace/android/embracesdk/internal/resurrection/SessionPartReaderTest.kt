package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
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
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var directoryStore: SessionPartDirectoryStore
    private lateinit var intakeService: FakeIntakeService
    private lateinit var writeTracker: SessionPartWriteTracker

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        directoryStore = SessionPartDirectoryStore(lazy { sessionsDir }, BackgroundWorker(executor), clock, logger)
        intakeService = FakeIntakeService()
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
    fun `session parts are delivered in the order they must be sent in`() {
        persist(laterPartDirectory)
        persist(partDirectory)
        createReader().readPersistedSessionParts()

        assertEquals(
            listOf(partDirectory.sessionPartId, laterPartDirectory.sessionPartId),
            intakeService.intakeList.map { it.metadata.sessionPartId },
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part that is still being written to is left alone`() {
        persist(partDirectory)
        writeTracker.markWriting(partDirectory.sessionPartId)

        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    @Test
    fun `a session part that cannot be reconstructed is deleted without being delivered`() {
        persist(partDirectory)
        assertTrue(File(File(sessionsDir, partDirectory.dirName), METADATA_FILE_NAME).delete())

        createReader().readPersistedSessionParts()

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertDeleted(partDirectory)
    }

    @Test
    fun `a session part that intake does not store is left on disk`() {
        persist(partDirectory)
        intakeService.storeSucceeds = false

        createReader().readPersistedSessionParts()

        // the part reached intake but wasn't stored, so the only copy of the telemetry is retained
        assertEquals(partDirectory.sessionPartId, intakeService.intakeList.single().metadata.sessionPartId)
        assertEquals(setOf(partDirectory), directoryStore.storedDirectories().toSet())
        assertEquals(setOf(partDirectory.dirName), sessionsDir.list()?.toSet() ?: emptySet<String>())
    }

    @Test
    fun `reading persisted session parts is a no-op when there is nothing on disk`() {
        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = emptyList())
    }

    @Test
    fun `everything on disk is deleted when multi file persistence is disabled`() {
        persist(partDirectory)
        persist(laterPartDirectory)
        assertTrue(File(sessionsDir, "unparseable.tmp").createNewFile())
        createReader(enabled = false).readPersistedSessionParts()

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertFalse(sessionsDir.exists())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `each session part is handed over to intake before the next one is delivered`() {
        persist(laterPartDirectory)
        persist(partDirectory)

        val recording = RecordingIntakeService()
        createReader(intakeService = recording).readPersistedSessionParts()
        assertEquals(
            listOf(
                "take:${partDirectory.sessionPartId}",
                "wait:${partDirectory.sessionPartId}",
                "take:${laterPartDirectory.sessionPartId}",
                "wait:${laterPartDirectory.sessionPartId}",
            ),
            recording.events,
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `the pass is abandoned when intake does not store a part in time`() {
        persist(laterPartDirectory)
        persist(partDirectory)

        val stalled = StalledIntakeService()
        createReader(intakeService = stalled).readPersistedSessionParts()

        assertEquals(listOf(partDirectory.sessionPartId), stalled.takenPartIds)
        assertEquals(setOf(partDirectory, laterPartDirectory), directoryStore.storedDirectories().toSet())
        assertEquals(
            setOf(partDirectory.dirName, laterPartDirectory.dirName),
            sessionsDir.list()?.toSet() ?: emptySet<String>(),
        )
        assertTrue(logger.internalErrorMessages.any { it.throwable is TimeoutException })
    }

    @Test
    fun `parts left behind by an abandoned pass are delivered by the next pass`() {
        persist(laterPartDirectory)
        persist(partDirectory)
        createReader(intakeService = StalledIntakeService()).readPersistedSessionParts()
        createReader().readPersistedSessionParts()

        assertEquals(
            listOf(partDirectory.sessionPartId, laterPartDirectory.sessionPartId),
            intakeService.intakeList.map { it.metadata.sessionPartId },
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part that intake drops without stalling does not abandon the pass`() {
        persist(laterPartDirectory)
        persist(partDirectory)
        intakeService.storeSucceeds = false
        createReader().readPersistedSessionParts()

        assertEquals(
            listOf(partDirectory.sessionPartId, laterPartDirectory.sessionPartId),
            intakeService.intakeList.map { it.metadata.sessionPartId },
        )
        assertEquals(setOf(partDirectory, laterPartDirectory), directoryStore.storedDirectories().toSet())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a session part that cannot be reconstructed does not abandon the pass`() {
        persist(laterPartDirectory)
        persist(partDirectory)
        assertTrue(File(File(sessionsDir, partDirectory.dirName), METADATA_FILE_NAME).delete())
        createReader().readPersistedSessionParts()

        assertEquals(
            listOf(laterPartDirectory.sessionPartId),
            intakeService.intakeList.map { it.metadata.sessionPartId },
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    private class RecordingIntakeService : IntakeService {
        val events: MutableList<String> = mutableListOf()

        override fun shutdown() {}

        override fun take(
            intake: Envelope<*>,
            metadata: StoredTelemetryMetadata,
            staleEntry: StoredTelemetryMetadata?,
            onStored: (() -> Unit)?,
        ): Future<*> {
            val partId = metadata.sessionPartId
            events.add("take:$partId")
            onStored?.invoke()
            return object : Future<Unit> {
                override fun cancel(mayInterruptIfRunning: Boolean) = false
                override fun isCancelled() = false
                override fun isDone() = true
                override fun get() = error("the reader must always wait with a timeout")
                override fun get(timeout: Long, unit: TimeUnit) {
                    events.add("wait:$partId")
                }
            }
        }
    }

    private class StalledIntakeService : IntakeService {
        val takenPartIds: MutableList<String> = mutableListOf()

        override fun shutdown() {}

        override fun take(
            intake: Envelope<*>,
            metadata: StoredTelemetryMetadata,
            staleEntry: StoredTelemetryMetadata?,
            onStored: (() -> Unit)?,
        ): Future<*> {
            takenPartIds.add(metadata.sessionPartId)
            return object : Future<Unit> {
                override fun cancel(mayInterruptIfRunning: Boolean) = false
                override fun isCancelled() = false
                override fun isDone() = false
                override fun get() = error("the reader must always wait with a timeout")
                override fun get(timeout: Long, unit: TimeUnit): Unit = throw TimeoutException("stalled")
            }
        }
    }

    private fun createReader(
        enabled: Boolean = true,
        intakeService: IntakeService = this.intakeService,
    ) = SessionPartReader(
        sessionsDir = lazy { sessionsDir },
        directoryStore = directoryStore,
        reconstructionService = SessionReconstructionService(lazy { sessionsDir }, logger),
        intakeService = intakeService,
        writeTracker = writeTracker,
        processIdProvider = { PROCESS_ID },
        configService = FakeConfigService(
            persistenceBehavior = createPersistenceBehavior(
                remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = if (enabled) 100.0f else 0.0f),
            ),
        ),
        logger = logger,
    )

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
        assertEquals(retained.toSet(), directoryStore.storedDirectories().toSet())
        assertEquals(retained.map(SessionPartDirectory::dirName).toSet(), sessionsDir.list()?.toSet() ?: emptySet<String>())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
