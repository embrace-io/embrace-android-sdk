package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
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
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
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
        assertNoInternalErrors()
    }

    private fun createWriter(enabled: Boolean = true) = SessionPartWriterImpl(
        lazy { sessionsDir },
        BackgroundWorker(executor),
        configService(enabled),
        TestUuidSource(),
        clock,
        logger,
    )

    private fun configService(enabled: Boolean) = FakeConfigService(
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
        return (sessionsDir.list() ?: emptyArray())
            .mapNotNull(SessionPartDirectory::fromDirName)
            .sortedWith(SessionPartDirectory.comparator)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
