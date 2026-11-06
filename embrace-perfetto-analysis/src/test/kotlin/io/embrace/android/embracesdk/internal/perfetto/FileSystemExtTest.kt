package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class FileSystemExtTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val fs get() = fsRule.fileSystem

    @Test
    fun `a symlink is described by what it points at`() {
        val file = "/real/trace.perfetto-trace".toPath()
        val dir = "/real/run".toPath()
        fs.createDirectories(dir)
        fs.write(file) { writeUtf8("0123456789") }
        fs.createSymlink("/trace-link".toPath(), file)
        fs.createSymlink("/run-link".toPath(), dir)

        assertTrue(checkNotNull(fs.resolvedMetadata("/trace-link".toPath())).isRegularFile)
        assertTrue(checkNotNull(fs.resolvedMetadata("/run-link".toPath())).isDirectory)
        assertEquals(10L, fs.sizeOf("/trace-link".toPath()))
    }

    @Test
    fun `a symlink to nothing is not there`() {
        fs.createSymlink("/dangling".toPath(), "/absent".toPath())
        assertNull(fs.resolvedMetadata("/dangling".toPath()))
        assertEquals(0L, fs.sizeOf("/dangling".toPath()))
    }

    @Test
    fun `a relative path is resolved against the working directory`() {
        assertEquals("/abs/x".toPath(), absolute("/abs/x".toPath()))
        assertEquals(checkNotNull(System.getProperty("user.dir")).toPath() / "rel", absolute("rel".toPath()))
    }
}
