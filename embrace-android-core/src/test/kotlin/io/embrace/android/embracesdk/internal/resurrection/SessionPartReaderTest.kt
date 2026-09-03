package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifestWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartReaderTest {

    private companion object {
        private const val PROCESS_ID = "cccccccccccccccccccccccccccccccc"
        private const val PERSISTED_PROCESS_ID = "dddddddddddddddddddddddddddddddd"
        private const val MANIFEST_FILE_NAME = "manifest.pb"

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
        assertTrue(File(File(sessionsDir, partDirectory.dirName), MANIFEST_FILE_NAME).delete())

        createReader().readPersistedSessionParts()

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertDeleted(partDirectory)
    }

    @Test
    fun `reading persisted session parts is a no-op when there is nothing on disk`() {
        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = emptyList())
    }

    @Test
    fun `reading persisted session parts is a no-op when multi file persistence is disabled`() {
        persist(partDirectory)

        createReader(enabled = false).readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    private fun createReader(enabled: Boolean = true) = SessionPartReader(
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

        SessionManifestWriter(lazy { sessionsDir }, logger).write(
            directory = directory,
            resource = EnvelopeResource(appVersion = "1.0.0"),
            envelopeVersion = SESSION_ENVELOPE_VERSION,
            envelopeType = SESSION_ENVELOPE_TYPE,
        )
        SessionMetadataWriter(
            sessionsDir = lazy { sessionsDir },
            sessionPartDirectorySource = { directory },
            metadataSource = { EnvelopeMetadata(username = "fake-user") },
            resourceSource = { EnvelopeResource(appVersion = "1.0.0") },
            logger = logger,
        ).write()
        SessionSpanWriter(lazy { sessionsDir }, { directory }, logger).write(span)
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
