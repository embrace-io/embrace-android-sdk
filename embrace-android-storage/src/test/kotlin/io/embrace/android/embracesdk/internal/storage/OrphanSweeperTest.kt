package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class OrphanSweeperTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val root = "/no_backup/xcor".toPath()
    private val marker = root / SdkStorageLayout.LAYOUT_VERSION_FILE_NAME
    private val mtimes = mutableMapOf<Path, Long?>()
    private val undeletable = mutableSetOf<Path>()
    private val unwritablePrefixes = mutableSetOf<String>()
    private var wallClockMs = PROCESS_START_MS + OrphanSweeper.MIN_STALE_AGE_MS
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private lateinit var fileSystem: FileSystem
    private lateinit var storage: SdkStorageImpl

    @Before
    fun setUp() {
        fileSystem = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun metadataOrNull(path: Path): FileMetadata? = super.metadataOrNull(path)?.let {
                if (path in mtimes) it.copy(lastModifiedAtMillis = mtimes[path]) else it
            }

            override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
                if (unwritablePrefixes.any { file.name.startsWith(it) }) throw IOException("can't write $file")
                return super.openReadWrite(file, mustCreate, mustExist)
            }

            override fun delete(path: Path, mustExist: Boolean) {
                if (path in undeletable) throw IOException("can't delete $path")
                super.delete(path, mustExist)
            }
        }
        storage = SdkStorageImpl(fileSystem, root, errorHandler)
    }

    @Test
    fun `declared entries are kept in every dir`() {
        val kept = listOf(
            SdkDir.REMOTE_CONFIG to "most_recent_response",
            SdkDir.REMOTE_CONFIG to "etag",
            SdkDir.REMOTE_CONFIG to "cached_config",
            SdkDir.INSTALL_STATE to "ordinals.bin",
            SdkDir.INSTALL_STATE to "persisted_sdk_state.bin",
            SdkDir.INSTALL_STATE to "network_capture.bin",
            SdkDir.INSTALL_STATE to "aei_delivered.bin",
            SdkDir.INSTALL_STATE to "crash_marker",
            SdkDir.TELEMETRY_READY to V2_PAYLOAD,
            SdkDir.TELEMETRY_PENDING_SNAPSHOTS to V2_PAYLOAD,
            SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES to V2_PAYLOAD,
            SdkDir.TELEMETRY_LOG_ENVELOPES to V2_PAYLOAD,
            SdkDir.TELEMETRY_PENDING_SESSION_PARTS to "$SESSION_PART_DIR/metadata.pb",
            SdkDir.TELEMETRY_PENDING_SESSION_PARTS to "$SESSION_PART_DIR/completed_spans.pb",
            SdkDir.TELEMETRY_PENDING_SESSION_PARTS to "$SESSION_PART_DIR/span_snapshots.pb",
        ).map { (dir, name) -> staleWithAncestors(write(storage.dir(dir) / name)) }

        assertEquals(0, sweep())

        kept.forEach { assertTrue("$it was deleted", fileSystem.exists(it)) }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `stale unknown entries are deleted at every level`() {
        val orphans = listOf(
            root / "unknown_file",
            root / "unknown_dir/file",
            root / "telemetry/unknown",
            root / "telemetry/pending/unknown_dir/file",
            storage.dir(SdkDir.REMOTE_CONFIG) / "old_config",
            storage.dir(SdkDir.INSTALL_STATE) / "embrace_crash_marker",
            storage.dir(SdkDir.TELEMETRY_READY) / "not_a_payload.json",
            storage.dir(SdkDir.TELEMETRY_READY) / "1_2_3_4_true_x_v3.json",
            storage.dir(SdkDir.TELEMETRY_READY) / V1_PAYLOAD,
            storage.dir(SdkDir.TELEMETRY_READY) / "$V2_PAYLOAD/nested",
            storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / "v2_1_uuid_none_none/metadata.pb",
            storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / "$SESSION_PART_DIR/unknown.pb",
        ).map { stale(write(it)) }
        orphans.forEach { stale(it.parent!!) }

        assertEquals(orphans.size, sweep())

        orphans.forEach { assertFalse("$it survived", fileSystem.exists(it)) }
        assertFalse(fileSystem.exists(root / "unknown_dir"))
        assertFalse(fileSystem.exists(storage.path(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / "v2_1_uuid_none_none"))
        assertTrue(fileSystem.exists(storage.path(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / SESSION_PART_DIR))
        assertTrue(fileSystem.exists(marker))
    }

    @Test
    fun `stale temp files are deleted and fresh ones kept`() {
        val staleTmp = stale(write(storage.dir(SdkDir.TELEMETRY_READY) / "$V2_PAYLOAD.tmp"))
        val staleSnapshotTmp = stale(write(storage.dir(SdkDir.TELEMETRY_PENDING_SNAPSHOTS) / "$V2_PAYLOAD.tmp"))
        val freshTmp = write(storage.dir(SdkDir.TELEMETRY_LOG_ENVELOPES) / "$V2_PAYLOAD.tmp")
        val partDir = storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / SESSION_PART_DIR
        val stalePartTmp = stale(write(partDir / "metadata.pb.tmp"))
        val freshPartTmp = write(partDir / "completed_spans.pb.tmp")
        val freshStateTmp = write(storage.dir(SdkDir.INSTALL_STATE) / "ordinals.bin.tmp")

        assertEquals(3, sweep())

        assertFalse(fileSystem.exists(staleTmp))
        assertFalse(fileSystem.exists(staleSnapshotTmp))
        assertFalse(fileSystem.exists(stalePartTmp))
        assertTrue(fileSystem.exists(freshTmp))
        assertTrue(fileSystem.exists(freshPartTmp))
        assertTrue(fileSystem.exists(freshStateTmp))
    }

    @Test
    fun `fresh unknown entries are only deleted outside leaf dirs`() {
        val freshInLeaf = write(storage.dir(SdkDir.TELEMETRY_READY) / "unknown")
        val freshInTelemetry = write(root / "telemetry/unknown")
        val freshInRoot = write(root / "unknown")

        assertEquals(2, sweep())

        assertTrue(fileSystem.exists(freshInLeaf))
        assertFalse(fileSystem.exists(freshInTelemetry))
        assertFalse(fileSystem.exists(freshInRoot))
    }

    @Test
    fun `a file where a declared dir belongs is deleted`() {
        val file = write(root / "telemetry")
        assertEquals(1, sweep())
        assertFalse(fileSystem.exists(file))
    }

    @Test
    fun `an absent marker is written and the sweep runs`() {
        val orphan = write(root / "orphan")
        assertEquals(1, sweep())
        assertFalse(fileSystem.exists(orphan))
        assertEquals("1", fileSystem.read(marker) { readUtf8() })
    }

    @Test
    fun `an equal marker sweeps`() {
        write(marker, "1")
        val orphan = write(root / "orphan")
        assertEquals(1, sweep())
        assertFalse(fileSystem.exists(orphan))
    }

    @Test
    fun `an older marker is raised and the sweep runs`() {
        write(marker, "0\n")
        val orphan = write(root / "orphan")
        assertEquals(1, sweep(layoutVersion = 3))
        assertFalse(fileSystem.exists(orphan))
        assertEquals("3", fileSystem.read(marker) { readUtf8() })
    }

    @Test
    fun `a newer or unparseable marker skips the sweep and is left alone`() {
        listOf("2\n", "2", "{\"version\":2}").forEach { contents ->
            write(marker, contents)
            val orphan = write(root / "orphan")

            assertEquals(0, sweep(layoutVersion = 1))

            assertTrue(fileSystem.exists(orphan))
            assertEquals(contents, fileSystem.read(marker) { readUtf8() })
        }
        assertEquals(3, errorHandler.warningMessages.size)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a marker that can't be written is tracked and the sweep still runs`() {
        unwritablePrefixes += "${SdkStorageLayout.LAYOUT_VERSION_FILE_NAME}."
        val orphan = write(root / "orphan")

        assertEquals(1, sweep())

        assertFalse(fileSystem.exists(orphan))
        assertFalse(fileSystem.exists(marker))
        assertEquals("OrphanSweepFail", errorHandler.sdkErrorMessages.single().msg)
    }

    @Test
    fun `entries modified at process start or with no mtime are kept`() {
        val atStart = write(storage.dir(SdkDir.TELEMETRY_READY) / "at_start")
        val noMtime = write(storage.dir(SdkDir.TELEMETRY_READY) / "no_mtime")
        mtimes[atStart] = PROCESS_START_MS
        mtimes[noMtime] = null

        assertEquals(0, sweep())

        assertTrue(fileSystem.exists(atStart))
        assertTrue(fileSystem.exists(noMtime))
    }

    @Test
    fun `entries younger than the minimum age are kept after the clock steps back`() {
        val inFlight = stale(write(storage.dir(SdkDir.TELEMETRY_READY) / "$V2_PAYLOAD.tmp"))
        val old = write(storage.dir(SdkDir.TELEMETRY_LOG_ENVELOPES) / "$V2_PAYLOAD.tmp")
        wallClockMs = STALE_MS + 1
        mtimes[old] = wallClockMs - OrphanSweeper.MIN_STALE_AGE_MS

        assertEquals(1, sweep())

        assertTrue(fileSystem.exists(inFlight))
        assertFalse(fileSystem.exists(old))
    }

    @Test
    fun `a missing root is not created`() {
        assertEquals(0, sweep())
        assertFalse(fileSystem.exists(root))
    }

    @Test
    fun `a failed delete is tracked and the sweep carries on`() {
        val blocked = write(root / "a_orphan")
        val other = write(root / "b_orphan")
        undeletable += blocked

        assertEquals(1, sweep())

        assertTrue(fileSystem.exists(blocked))
        assertFalse(fileSystem.exists(other))
        assertEquals("OrphanSweepFail", errorHandler.sdkErrorMessages.single().msg)
    }

    private fun sweep(layoutVersion: Int = SdkStorageLayout.LAYOUT_VERSION): Int =
        OrphanSweeper(storage, layoutVersion, PROCESS_START_MS, { wallClockMs }, errorHandler).sweep()

    private fun write(path: Path, contents: String = "data"): Path {
        path.parent?.let(fileSystem::createDirectories)
        fileSystem.write(path) { writeUtf8(contents) }
        return path
    }

    private fun stale(path: Path): Path = path.also { mtimes[it] = STALE_MS }

    private fun staleWithAncestors(path: Path): Path = path.also {
        generateSequence(it) { p -> p.parent }.takeWhile { p -> p != root }.forEach(::stale)
    }

    private companion object {
        const val STALE_MS = 1_000L
        const val PROCESS_START_MS = 2_000L
        const val V1_PAYLOAD = "p1_1700000000000_uuid_pid_true_session_v1.json"
        const val V2_PAYLOAD = "p1_1700000000000_uuid_pid_true_session_none_none_v2.json"
        const val SESSION_PART_DIR = "v1_1700000000000_uuid_user_part"
    }
}
