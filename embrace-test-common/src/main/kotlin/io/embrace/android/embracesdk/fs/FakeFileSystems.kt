package io.embrace.android.embracesdk.fs

import okio.fakefilesystem.FakeFileSystem
import org.junit.rules.ExternalResource

/**
 * Creates the [FakeFileSystem] that tests should use in place of the real disk.
 */
fun fakeFileSystem(): FakeFileSystem = FakeFileSystem().apply { emulateUnix() }

/**
 * Supplies a fresh [fakeFileSystem] for each test, and fails the test if it leaves a file handle open.
 *
 * ```
 * @get:Rule
 * val fsRule = FakeFileSystemRule()
 * ```
 */
class FakeFileSystemRule : ExternalResource() {

    private var current: FakeFileSystem? = null

    val fileSystem: FakeFileSystem
        get() = checkNotNull(current) { "FakeFileSystemRule is only available while a test is running" }

    override fun before() {
        current = fakeFileSystem()
    }

    override fun after() {
        val fs = current ?: return
        current = null
        fs.checkNoOpenFiles()
    }
}
