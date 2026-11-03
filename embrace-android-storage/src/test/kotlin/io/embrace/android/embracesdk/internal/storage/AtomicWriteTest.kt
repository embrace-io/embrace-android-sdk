package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class AtomicWriteTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val dir = "/state".toPath()
    private val target = dir / "file.bin"
    private val tmp = dir / "file.bin$ATOMIC_WRITE_TMP_SUFFIX"

    @Before
    fun setUp() {
        fsRule.fileSystem.createDirectories(dir)
    }

    @Test
    fun `write creates the target`() {
        fsRule.fileSystem.writeAtomically(target) { it.writeUtf8("hello") }
        assertEquals("hello", read(target))
        assertFalse(fsRule.fileSystem.exists(tmp))
    }

    @Test
    fun `write replaces existing content`() {
        fsRule.fileSystem.write(target) { writeUtf8("a much longer original value") }
        fsRule.fileSystem.writeAtomically(target) { it.writeUtf8("new") }
        assertEquals("new", read(target))
        assertFalse(fsRule.fileSystem.exists(tmp))
    }

    @Test
    fun `stale temp file is truncated before writing`() {
        fsRule.fileSystem.write(tmp) { writeUtf8("leftover from a previous crash") }
        fsRule.fileSystem.writeAtomically(target) { it.writeUtf8("new") }
        assertEquals("new", read(target))
        assertFalse(fsRule.fileSystem.exists(tmp))
    }

    @Test
    fun `missing parent dir throws and leaves nothing behind`() {
        val missing = "/missing/file.bin".toPath()
        assertThrows(IOException::class.java) {
            fsRule.fileSystem.writeAtomically(missing) { it.writeUtf8("x") }
        }
        assertFalse(fsRule.fileSystem.exists("/missing".toPath()))
    }

    @Test
    fun `failed rename keeps the original and removes the temp file`() {
        fsRule.fileSystem.write(target) { writeUtf8("original") }
        val failingMove = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) = throw IOException("rename failed")
        }
        assertThrows(IOException::class.java) {
            failingMove.writeAtomically(target) { it.writeUtf8("new") }
        }
        assertEquals("original", read(target))
        assertFalse(fsRule.fileSystem.exists(tmp))
    }

    @Test
    fun `failing block keeps the original and removes the temp file`() {
        fsRule.fileSystem.write(target) { writeUtf8("original") }
        assertThrows(IllegalStateException::class.java) {
            fsRule.fileSystem.writeAtomically(target) {
                it.writeUtf8("partial")
                error("encode failed")
            }
        }
        assertEquals("original", read(target))
        assertFalse(fsRule.fileSystem.exists(tmp))
    }

    private fun read(path: Path): String = fsRule.fileSystem.read(path) { readUtf8() }
}
