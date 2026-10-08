package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartWriterTrackerTest {

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
    private lateinit var writeTracker: SessionPartWriteTracker
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private lateinit var writer: SessionPartWriter

    private val resourceSource = object : EnvelopeResourceSource {
        override fun getEnvelopeResource(): EnvelopeResource = EnvelopeResource()
        override fun add(key: String, value: String) = Unit
        override fun addChangeListener(listener: (EnvelopeResource) -> Unit) = Unit
    }

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        writeTracker = SessionPartWriteTracker()
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply {
            sessionPartSpan = FakeEmbraceSdkSpan().apply { start(clock.now()) }
        }
        writer = SessionPartWriterImpl(
            lazy { sessionsDir },
            BackgroundWorker(executor),
            FakeConfigService(
                config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { true }) }),
            ),
            TestUuidSource(),
            clock,
            FakeInternalLogger(throwOnInternalError = false),
            resourceSource,
            { EnvelopeMetadata() },
            currentSessionPartSpan,
            { emptyList() },
            FakeInternalTelemetryService(),
            writeTracker = writeTracker,
        )
    }

    @Test
    fun `a session part is marked as written to as soon as it starts`() {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        executor.runCurrentlyBlocked()

        // the directory marked is the one the part is written into
        assertTrue(writeTracker.isWriting(directoryOnDisk(SESSION_PART_ID)))
    }

    @Test
    fun `an ended session part is still marked until its queued writes have run`() {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        executor.runCurrentlyBlocked()
        val directory = directoryOnDisk(SESSION_PART_ID)

        currentSessionPartSpan.endSession(startNewSession = false)
        writer.onSessionPartEnded(SESSION_PART_ID)
        assertTrue(writeTracker.isWriting(directory))

        executor.runCurrentlyBlocked()
        assertFalse(writeTracker.isWriting(directory))
    }

    @Test
    fun `a session part displaced by the next one stops being marked`() {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        executor.runCurrentlyBlocked()

        // the part loses its end, so starting the next one has to complete it
        currentSessionPartSpan.endSession(startNewSession = true)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, OTHER_SESSION_PART_ID)
        executor.runCurrentlyBlocked()

        assertFalse(writeTracker.isWriting(directoryOnDisk(SESSION_PART_ID)))
        assertTrue(writeTracker.isWriting(directoryOnDisk(OTHER_SESSION_PART_ID)))
    }

    @Test
    fun `a session part that never ends stays marked`() {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, SESSION_PART_ID)
        executor.runCurrentlyBlocked()
        writer.onCrash()
        assertTrue(writeTracker.isWriting(directoryOnDisk(SESSION_PART_ID)))
    }

    private fun directoryOnDisk(sessionPartId: String): SessionPartDirectory =
        sessionsDir.list()
            .orEmpty()
            .mapNotNull(SessionPartDirectory::fromDirName)
            .single { it.sessionPartId == sessionPartId }
}
