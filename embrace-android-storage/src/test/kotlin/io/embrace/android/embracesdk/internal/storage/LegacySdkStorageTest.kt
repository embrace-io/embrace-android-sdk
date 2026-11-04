package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class LegacySdkStorageTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val filesDir = "/data/files".toPath()
    private val cacheDir = "/data/cache".toPath()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private var cacheDirResolved = false

    private val storage by lazy {
        LegacySdkStorage(
            fileSystem = fsRule.fileSystem,
            filesDir = filesDir,
            cacheDir = {
                cacheDirResolved = true
                cacheDir
            },
            errorHandler = errorHandler,
        )
    }

    @Test
    fun `every dir resolves to its 9x path in the files dir`() {
        val expected = mapOf(
            SdkDir.REMOTE_CONFIG to "embrace_remote_config",
            SdkDir.INSTALL_STATE to "embrace",
            SdkDir.TELEMETRY_READY to "embrace_payloads",
            SdkDir.TELEMETRY_PENDING_SNAPSHOTS to "embrace_cache",
            SdkDir.TELEMETRY_PENDING_SESSION_PARTS to "embrace_sessions_split",
            SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES to "embrace_native",
            SdkDir.TELEMETRY_LOG_ENVELOPES to "embrace_envelopes",
        )
        assertEquals(expected, LegacySdkStorage.LEGACY_DIR_NAMES)
        expected.forEach { (dir, name) ->
            val path = storage.dir(dir)
            assertEquals(filesDir / name, path)
            assertTrue(isDirectory(path))
        }
        assertFalse(cacheDirResolved)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `path does not create the dir`() {
        assertEquals(filesDir / "embrace_remote_config", storage.path(SdkDir.REMOTE_CONFIG))
        assertFalse(fsRule.fileSystem.exists(filesDir))
    }

    @Test
    fun `dirs without a 9x location track an error and resolve to the files dir`() {
        val containers = SdkDir.entries - LegacySdkStorage.LEGACY_DIR_NAMES.keys
        assertEquals(setOf(SdkDir.TELEMETRY, SdkDir.TELEMETRY_PENDING), containers.toSet())

        containers.forEach { dir ->
            assertEquals(filesDir, storage.path(dir))
            assertEquals(filesDir, storage.dir(dir))
            assertEquals(filesDir, storage.dir(dir))
        }
        // one for each path() call, and one for each dir as dir() resolves it once
        assertEquals(List(containers.size * 2) { "PayloadStorageFail" }, errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `telemetry falls back to the same name in the cache dir when the files dir one can't be created`() {
        writeData(filesDir)
        val telemetryDirs = LegacySdkStorage.LEGACY_DIR_NAMES.keys.filter { it.isTelemetry }

        telemetryDirs.forEach { dir ->
            val path = storage.dir(dir)
            assertEquals(cacheDir / LegacySdkStorage.LEGACY_DIR_NAMES.getValue(dir), path)
            assertEquals(path, storage.path(dir))
            assertTrue(isDirectory(path))
        }
        assertEquals(List(telemetryDirs.size) { "PayloadStorageFail" }, errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a fallback that can't be resolved uses the files dir location and is tracked once`() {
        val storage = LegacySdkStorage(fsRule.fileSystem, filesDir, { error("no cache dir") }, errorHandler)
        writeData(filesDir)

        assertEquals(filesDir / "embrace_payloads", storage.dir(SdkDir.TELEMETRY_READY))
        assertEquals(filesDir / "embrace_payloads", storage.dir(SdkDir.TELEMETRY_READY))
        assertEquals(filesDir / "embrace", storage.dir(SdkDir.INSTALL_STATE))
        assertEquals(listOf("PayloadStorageFail", "PayloadStorageFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `install state falls back to the cache dir itself`() {
        writeData(filesDir)
        assertEquals(cacheDir, storage.dir(SdkDir.INSTALL_STATE))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `remote config has no fallback`() {
        writeData(filesDir)
        assertEquals(filesDir / "embrace_remote_config", storage.dir(SdkDir.REMOTE_CONFIG))
        assertFalse(cacheDirResolved)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `deleteTelemetry deletes every legacy telemetry dir under both roots and keeps other files`() {
        val telemetryNames = LegacySdkStorage.LEGACY_DIR_NAMES.values - setOf("embrace_remote_config", "embrace")
        val telemetryFiles = telemetryNames.flatMap { name ->
            listOf(filesDir, cacheDir).map { root -> (root / name / "payload").also(::writeData) }
        }
        val otherFiles = listOf(filesDir, cacheDir).map { (it / "app-file").also(::writeData) } + listOf(
            (filesDir / "embrace_remote_config" / "most_recent_response").also(::writeData),
            (filesDir / "embrace" / "embrace_crash_marker").also(::writeData),
        )
        storage.deleteTelemetry()

        telemetryFiles.forEach { assertFalse(fsRule.fileSystem.exists(checkNotNull(it.parent))) }
        otherFiles.forEach { assertTrue(fsRule.fileSystem.exists(it)) }
    }

    @Test
    fun `deleteTelemetry carries on past a dir that can't be deleted`() {
        val blocked = filesDir / "embrace_payloads" / "payload"
        val storage = LegacySdkStorage(failingDeletes(blocked), filesDir, { cacheDir }, errorHandler)
        val telemetryNames = LegacySdkStorage.LEGACY_DIR_NAMES.filterKeys { it.isTelemetry }.values
        val others = listOf(filesDir, cacheDir).flatMap { root -> telemetryNames.map { root / it / "payload" } } - blocked
        (others + blocked).forEach(::writeData)

        storage.deleteTelemetry()

        assertTrue(fsRule.fileSystem.exists(blocked))
        others.forEach { assertFalse(fsRule.fileSystem.exists(it)) }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `the cache dir crash marker is not an orphan`() {
        assertEquals(listOf("emb_config_cache"), LegacySdkStorage.ORPHANED_CACHE_DIR_ENTRIES)
    }

    @Test
    fun `deleteTelemetry still deletes from the files dir if the cache dir can't be resolved`() {
        val storage = LegacySdkStorage(fsRule.fileSystem, filesDir, { error("no cache dir") }, errorHandler)
        val payload = (filesDir / "embrace_payloads" / "payload").also(::writeData)
        storage.deleteTelemetry()
        assertFalse(fsRule.fileSystem.exists(payload))
    }

    private fun failingDeletes(blocked: Path): FileSystem = object : ForwardingFileSystem(fsRule.fileSystem) {
        override fun delete(path: Path, mustExist: Boolean) {
            if (path == blocked) throw IOException("can't delete $path")
            super.delete(path, mustExist)
        }
    }

    private fun writeData(path: Path) {
        fsRule.fileSystem.createDirectories(checkNotNull(path.parent))
        fsRule.fileSystem.write(path) { writeUtf8("data") }
    }

    private fun isDirectory(path: Path): Boolean = fsRule.fileSystem.metadataOrNull(path)?.isDirectory == true
}
