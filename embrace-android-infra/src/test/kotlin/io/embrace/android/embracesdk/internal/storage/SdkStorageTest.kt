package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SdkStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var storage: SdkStorage

    @Before
    fun setUp() {
        root = File(tmp.root, SdkStorageLayout.ROOT_DIR_NAME)
        storage = SdkStorage(root)
    }

    @Test
    fun `every dir resolves under the root`() {
        SdkDir.entries.forEach { dir ->
            val file = storage.dir(dir)
            assertTrue(file.isDirectory)
            assertTrue(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
            assertEquals(dir.dirName, file.name)
            assertEquals(dir.parent?.let(storage::dir) ?: root, file.parentFile)
        }
    }

    @Test
    fun `dirs are only created when accessed`() {
        storage.dir(SdkDir.TELEMETRY_PENDING_SNAPSHOTS)
        assertTrue(File(root, "telemetry/pending/snapshots").isDirectory)
        assertFalse(File(root, "remote_config").exists())
        assertFalse(File(root, "telemetry/ready").exists())
    }

    @Test
    fun `deleteTelemetry leaves remote config and install state`() {
        SdkDir.entries.forEach { File(storage.dir(it), "file").writeText("data") }
        storage.deleteTelemetry()
        assertFalse(File(root, "telemetry").exists())
        assertTrue(File(storage.dir(SdkDir.REMOTE_CONFIG), "file").exists())
        assertTrue(File(storage.dir(SdkDir.INSTALL_STATE), "file").exists())
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
        val errorHandler = FakeErrorHandler()
        val created = createSdkStorage(tmp.root, errorHandler)
        assertEquals(root, created.root)
        assertTrue(root.isDirectory)
    }

    @Test
    fun `createSdkStorage tracks a failure and still returns the path`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val noBackupFilesDir = tmp.newFile("not-a-dir")
        val created = createSdkStorage(noBackupFilesDir, errorHandler)
        assertEquals(File(noBackupFilesDir, SdkStorageLayout.ROOT_DIR_NAME), created.root)
        assertEquals("StorageRootFail", errorHandler.sdkErrorMessages.single().msg)
    }
}
