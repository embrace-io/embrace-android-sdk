package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartReaderTest {

    private companion object {
        private const val PROCESS_ID = "cccccccccccccccccccccccccccccccc"

        private val partDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
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

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        directoryStore = SessionPartDirectoryStore(lazy { sessionsDir }, BackgroundWorker(executor), clock, logger)
        intakeService = FakeIntakeService()
    }

    @Test
    fun `reading persisted session parts delivers nothing while the implementation is a stub`() {
        create(partDirectory)

        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    @Test
    fun `reading persisted session parts is a no-op when there is nothing on disk`() {
        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = emptyList())
    }

    @Test
    fun `reading persisted session parts is a no-op when multi file persistence is disabled`() {
        create(partDirectory)

        createReader(enabled = false).readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    private fun createReader(enabled: Boolean = true) = SessionPartReader(
        directoryStore = directoryStore,
        reconstructionService = SessionReconstructionService(lazy { sessionsDir }, logger),
        intakeService = intakeService,
        processIdProvider = { PROCESS_ID },
        configService = FakeConfigService(
            persistenceBehavior = createPersistenceBehavior(
                remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = if (enabled) 100.0f else 0.0f),
            ),
        ),
        logger = logger,
    )

    /**
     * Creates the directory via the store, then drains the worker so the work has completed.
     */
    private fun create(directory: SessionPartDirectory) {
        directoryStore.create(directory)
        executor.runCurrentlyBlocked()
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
