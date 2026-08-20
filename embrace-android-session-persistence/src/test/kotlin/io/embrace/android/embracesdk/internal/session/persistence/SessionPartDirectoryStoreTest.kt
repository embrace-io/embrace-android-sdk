package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionPartDirectoryStoreTest {

    private companion object {
        private const val NOW = FakeClock.DEFAULT_FAKE_CURRENT_TIME
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1_000L
        private const val MAX_AGE_MS = 7L * ONE_DAY_MS
        private const val STORAGE_LIMIT = 500

        private val partDirectory = SessionPartDirectory(
            timestamp = NOW,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var store: SessionPartDirectoryStore

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock)
        logger = FakeInternalLogger(throwOnInternalError = false)
        store = createStore()
    }

    @Test
    fun `nothing is created until the worker runs`() {
        store.create(partDirectory)
        assertFalse(partDir(partDirectory).exists())

        executor.runCurrentlyBlocked()
        assertTrue(partDir(partDirectory).isDirectory)
        assertNoInternalErrors()
    }

    @Test
    fun `directory is created with the expected name`() {
        create(partDirectory)
        assertEquals(listOf(partDirectory.dirName), dirNames())
        assertEquals(partDirectory, SessionPartDirectory.fromDirName(partDirectory.dirName))
        assertNoInternalErrors()
    }

    @Test
    fun `directory with empty session ids round trips`() {
        val directory = SessionPartDirectory(timestamp = NOW, uuid = UUID)
        create(directory)

        val dirName = checkNotNull(dirNames().singleOrNull())
        assertTrue(dirName.endsWith("_none_none"))
        with(checkNotNull(SessionPartDirectory.fromDirName(dirName))) {
            assertEquals(directory, this)
            assertEquals("", userSessionId)
            assertEquals("", sessionPartId)
        }
        assertNoInternalErrors()
    }

    @Test
    fun `creating the same directory twice is idempotent`() {
        create(partDirectory)
        create(partDirectory)
        assertEquals(listOf(partDirectory.dirName), dirNames())
        assertNoInternalErrors()
    }

    @Test
    fun `the sessions root is created when it does not exist yet`() {
        val absentRoot = File(sessionsDir, "nested/sessions")
        store = createStore(root = absentRoot)
        create(partDirectory)

        assertTrue(File(absentRoot, partDirectory.dirName).isDirectory)
        assertNoInternalErrors()
    }

    @Test
    fun `unparseable entries are deleted when the store is first used`() {
        File(sessionsDir, "junk.txt").writeText("not a session part")
        File(sessionsDir, "manifest.pb.tmp").writeText("orphaned temp file")
        File(sessionsDir, "not-a-session-part").apply {
            mkdirs()
            File(this, MANIFEST_FILE_NAME).writeText("stale")
        }
        create(partDirectory)

        assertEquals(listOf(partDirectory.dirName), dirNames())
        assertNoInternalErrors()
    }

    @Test
    fun `valid existing directories are retained`() {
        val existing = partDirectory.copy(timestamp = NOW - 1, uuid = "d3721de2-490a-533b-cacd-36423d8b6aab")
        createOnDisk(existing)
        create(partDirectory)

        assertEquals(setOf(existing.dirName, partDirectory.dirName), dirNames().toSet())
        assertNoInternalErrors()
    }

    @Test
    fun `directories older than the max age are pruned with their contents`() {
        val stale = partDirectory.copy(timestamp = NOW - MAX_AGE_MS - 1)
        val fresh = partDirectory.copy(timestamp = NOW - MAX_AGE_MS + ONE_DAY_MS)
        createOnDisk(stale)
        createOnDisk(fresh)
        create(partDirectory)

        assertEquals(setOf(fresh.dirName, partDirectory.dirName), dirNames().toSet())
        assertFalse(partDir(stale).exists())
        assertNoInternalErrors()
    }

    @Test
    fun `directories are pruned oldest first when the count limit is reached`() {
        store = createStore(storageLimit = 3)
        val directories = (0..3).map { partDirectory.copy(timestamp = NOW + it) }
        directories.forEach(::create)

        assertEquals(directories.drop(1).map(SessionPartDirectory::dirName).toSet(), dirNames().toSet())
        assertNoInternalErrors()
    }

    @Test
    fun `the new directory is dropped when it is the oldest beyond the limit`() {
        store = createStore(storageLimit = 2)
        val newer = (1..2).map { partDirectory.copy(timestamp = NOW + it) }
        newer.forEach(::create)

        val oldest = partDirectory.copy(timestamp = NOW)
        create(oldest)

        assertEquals(newer.map(SessionPartDirectory::dirName).toSet(), dirNames().toSet())
        assertFalse(partDir(oldest).exists())
        assertNoInternalErrors()
    }

    @Test
    fun `failure is tracked when the sessions root is a regular file`() {
        val occupiedRoot = File(sessionsDir, "occupied").apply { writeText("not a directory") }
        store = createStore(root = occupiedRoot)
        create(partDirectory)

        assertEquals("not a directory", occupiedRoot.readText())
        assertStoreFailureTracked()
    }

    private fun createStore(
        root: File = sessionsDir,
        storageLimit: Int = STORAGE_LIMIT,
    ) = SessionPartDirectoryStore(
        lazy { root },
        BackgroundWorker(executor),
        clock,
        logger,
        storageLimit,
        MAX_AGE_MS,
    )

    /**
     * Creates the directory via the store, then drains the worker so the work has completed.
     */
    private fun create(directory: SessionPartDirectory) {
        store.create(directory)
        executor.runCurrentlyBlocked()
    }

    /**
     * Creates a populated directory on disk without involving the store, standing in for a
     * directory left behind by a previous process.
     */
    private fun createOnDisk(directory: SessionPartDirectory): File =
        partDir(directory).apply {
            mkdirs()
            File(this, MANIFEST_FILE_NAME).writeText("previous process")
        }

    private fun partDir(directory: SessionPartDirectory): File = File(sessionsDir, directory.dirName)

    private fun dirNames(): List<String> = sessionsDir.list()?.toList() ?: emptyList()

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertStoreFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionPartDirectoryStoreFail", logger.internalErrorMessages.single().msg)
    }
}
