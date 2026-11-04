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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class SdkStorageTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val noBackupFilesDir = "/no_backup".toPath()
    private val root = noBackupFilesDir / SdkStorageLayout.ROOT_DIR_NAME
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private lateinit var storage: SdkStorageImpl

    @Before
    fun setUp() {
        storage = SdkStorageImpl(fsRule.fileSystem, root, errorHandler)
    }

    @Test
    fun `every dir resolves under the root`() {
        SdkDir.entries.forEach { dir ->
            val path = storage.dir(dir)
            assertTrue(isDirectory(path))
            assertEquals(dir.dirName, path.name)
            assertEquals(dir.parent?.let(storage::dir) ?: root, path.parent)
        }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `dirs are only created when accessed`() {
        storage.dir(SdkDir.TELEMETRY_PENDING_SNAPSHOTS)
        assertTrue(isDirectory(root / "telemetry/pending/snapshots"))
        assertEquals(root / "remote_config", storage.path(SdkDir.REMOTE_CONFIG))
        assertFalse(fsRule.fileSystem.exists(root / "remote_config"))
        assertFalse(fsRule.fileSystem.exists(root / "telemetry/ready"))
    }

    @Test
    fun `a dir that can't be created is tracked and its path still returned`() {
        writeData(root)

        assertEquals(root / "telemetry/ready", storage.dir(SdkDir.TELEMETRY_READY))
        assertEquals(root / "remote_config", storage.dir(SdkDir.REMOTE_CONFIG))
        assertEquals(listOf("PayloadStorageFail", "StorageRootFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `deleteTelemetry leaves remote config and install state`() {
        SdkDir.entries.forEach { writeData(storage.dir(it) / "file") }
        storage.deleteTelemetry()
        assertFalse(fsRule.fileSystem.exists(root / "telemetry"))
        assertTrue(fsRule.fileSystem.exists(storage.dir(SdkDir.REMOTE_CONFIG) / "file"))
        assertTrue(fsRule.fileSystem.exists(storage.dir(SdkDir.INSTALL_STATE) / "file"))
    }

    @Test
    fun `deleteTelemetry carries on past a dir that can't be deleted`() {
        val blocked = root / "telemetry/ready/file"
        val storage = SdkStorageImpl(failingDeletes(blocked), root, errorHandler)
        val leaves = SdkDir.entries.filter { it.isTelemetry && SdkStorageLayout.children(it).isEmpty() }
        leaves.forEach { writeData(storage.dir(it) / "file") }

        storage.deleteTelemetry()

        assertTrue(fsRule.fileSystem.exists(blocked))
        (leaves - SdkDir.TELEMETRY_READY).forEach {
            assertFalse(fsRule.fileSystem.exists(storage.path(it)))
        }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `layout children cover every dir exactly once`() {
        val all = listOf(null, *SdkDir.entries.toTypedArray()).flatMap(SdkStorageLayout::children)
        assertEquals(SdkDir.entries.toSet(), all.toSet())
        assertEquals(SdkDir.entries.size, all.size)
        assertEquals(
            listOf(SdkDir.REMOTE_CONFIG, SdkDir.INSTALL_STATE, SdkDir.TELEMETRY),
            SdkStorageLayout.children(null),
        )
    }

    @Test
    fun `createSdkStorage roots storage in the no-backup dir`() {
        val created = createSdkStorage(fsRule.fileSystem, noBackupFilesDir, FakeErrorHandler())
        assertEquals(root, created.root)
        assertTrue(isDirectory(root))
    }

    @Test
    fun `createSdkStorage tracks a failure and still returns the path`() {
        writeData(noBackupFilesDir)
        val created = createSdkStorage(fsRule.fileSystem, noBackupFilesDir, errorHandler)
        assertEquals(root, created.root)
        assertEquals("StorageRootFail", errorHandler.sdkErrorMessages.single().msg)
    }

    private fun failingDeletes(vararg blocked: Path): FileSystem = object : ForwardingFileSystem(fsRule.fileSystem) {
        override fun delete(path: Path, mustExist: Boolean) {
            if (path in blocked) throw IOException("can't delete $path")
            super.delete(path, mustExist)
        }
    }

    private fun writeData(path: Path) {
        path.parent?.let(fsRule.fileSystem::createDirectories)
        fsRule.fileSystem.write(path) { writeUtf8("data") }
    }

    private fun isDirectory(path: Path): Boolean = fsRule.fileSystem.metadataOrNull(path)?.isDirectory == true
}
