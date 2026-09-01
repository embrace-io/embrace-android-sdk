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
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers rewriting the manifest when the envelope resource changes. Most of the resource is fixed
 * for the lifetime of the process, but values such as the React Native bundle id can mutate.
 */
internal class SessionPartWriterResourceChangeTest {

    private companion object {
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val FIRST_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val SECOND_PART_ID = "cccccccccccccccccccccccccccccccc"
        private const val MANIFEST_FILE_NAME = "manifest.pb"
        private const val INITIAL_VERSION = "1.0.0"
        private const val CHANGED_VERSION = "2.0.0"
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan

    private var appVersion = INITIAL_VERSION
    private var resourceReads = 0
    private val resourceListeners = mutableListOf<(EnvelopeResource) -> Unit>()

    private val resourceSource = object : EnvelopeResourceSource {
        override fun getEnvelopeResource(): EnvelopeResource {
            resourceReads++
            return EnvelopeResource(appVersion = appVersion)
        }

        override fun add(key: String, value: String) = Unit

        override fun addChangeListener(listener: (EnvelopeResource) -> Unit) {
            resourceListeners.add(listener)
            listener(getEnvelopeResource())
        }
    }

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        appVersion = INITIAL_VERSION
        resourceReads = 0
        resourceListeners.clear()
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply {
            sessionPartSpan = FakeEmbraceSdkSpan().apply { start(clock.now()) }
        }
    }

    @Test
    fun `a resource change rewrites the manifest for the active session part`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        assertEquals(INITIAL_VERSION, manifestOnDisk(FIRST_PART_ID)?.resource?.app_version)

        changeResource(CHANGED_VERSION)
        drain()

        with(checkNotNull(manifestOnDisk(FIRST_PART_ID))) {
            assertEquals(CHANGED_VERSION, resource?.app_version)
            assertEquals(USER_SESSION_ID, user_session_id)
            assertEquals(FIRST_PART_ID, session_part_id)
        }
        assertNoInternalErrors()
    }

    @Test
    fun `a resource change before any session part starts writes nothing`() {
        createWriter()
        changeResource(CHANGED_VERSION)
        drain()

        assertTrue(resourceListeners.isEmpty())
        assertEquals(emptyList<SessionPartDirectory>(), partDirs())
        assertEquals(0, resourceReads)
        assertNoInternalErrors()
    }

    @Test
    fun `no listener is registered when multi file persistence is disabled`() {
        val writer = createWriter(enabled = false)
        startPart(writer, FIRST_PART_ID)
        drain()
        changeResource(CHANGED_VERSION)
        drain()

        assertTrue(resourceListeners.isEmpty())
        assertEquals(emptyList<SessionPartDirectory>(), partDirs())
        assertEquals(0, resourceReads)
        assertNoInternalErrors()
    }

    @Test
    fun `a resource change after a boundary rewrites only the newer manifest`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        endPart(writer, FIRST_PART_ID)
        startPart(writer, SECOND_PART_ID)
        drain()

        changeResource(CHANGED_VERSION)
        drain()

        assertEquals(INITIAL_VERSION, manifestOnDisk(FIRST_PART_ID)?.resource?.app_version)
        assertEquals(CHANGED_VERSION, manifestOnDisk(SECOND_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    @Test
    fun `a burst of resource changes is coalesced into one manifest write`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        assertEquals(2, resourceReads)

        repeat(3) { changeResource("2.0.$it") }
        drain()

        assertEquals(3, resourceReads)
        assertEquals("2.0.2", manifestOnDisk(FIRST_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    @Test
    fun `the resource listener is registered once across session parts`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        endPart(writer, FIRST_PART_ID)
        startPart(writer, SECOND_PART_ID)
        drain()

        assertEquals(1, resourceListeners.size)
        assertNoInternalErrors()
    }

    @Test
    fun `a resource change after a crash does not write`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        writer.onCrash()

        val readsBeforeChange = resourceReads
        changeResource(CHANGED_VERSION)

        assertEquals(readsBeforeChange, resourceReads)
        assertEquals(INITIAL_VERSION, manifestOnDisk(FIRST_PART_ID)?.resource?.app_version)
        assertNoInternalErrors()
    }

    @Test
    fun `a rewrite into a deleted session part directory is reported`() {
        val writer = createWriter()
        startPart(writer, FIRST_PART_ID)
        drain()
        assertTrue(File(sessionsDir, dirFor(FIRST_PART_ID).dirName).deleteRecursively())

        changeResource(CHANGED_VERSION)
        drain()

        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionManifestWriteFail", logger.internalErrorMessages.single().msg)
    }

    private fun createWriter(enabled: Boolean = true) = SessionPartWriterImpl(
        lazy { sessionsDir },
        BackgroundWorker(executor),
        FakeConfigService(
            persistenceBehavior = when {
                enabled -> createPersistenceBehavior(
                    remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
                )
                else -> createPersistenceBehavior()
            },
        ),
        TestUuidSource(),
        clock,
        logger,
        resourceSource,
        { EnvelopeMetadata(userId = "my-user-id") },
        currentSessionPartSpan,
    )

    private fun startPart(writer: SessionPartWriter, sessionPartId: String) {
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, sessionPartId)
    }

    private fun endPart(writer: SessionPartWriter, sessionPartId: String) {
        currentSessionPartSpan.endSession(startNewSession = true)
        writer.onSessionPartEnded(sessionPartId)
    }

    private fun changeResource(version: String) {
        appVersion = version
        val resource = EnvelopeResource(appVersion = version)
        resourceListeners.forEach { listener -> listener(resource) }
    }

    private fun drain() {
        executor.runCurrentlyBlocked()
    }

    private fun partDirs(): List<SessionPartDirectory> =
        (sessionsDir.list() ?: emptyArray())
            .mapNotNull(SessionPartDirectory::fromDirName)
            .sortedWith(SessionPartDirectory.comparator)

    private fun dirFor(sessionPartId: String): SessionPartDirectory =
        partDirs().single { it.sessionPartId == sessionPartId }

    private fun manifestOnDisk(sessionPartId: String): SessionManifest? =
        File(File(sessionsDir, dirFor(sessionPartId).dirName), MANIFEST_FILE_NAME)
            .takeIf(File::isFile)
            ?.inputStream()
            ?.use(SessionManifest.ADAPTER::decode)

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }
}
