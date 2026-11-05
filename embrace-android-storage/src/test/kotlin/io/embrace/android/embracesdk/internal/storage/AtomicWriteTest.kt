package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileHandle
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

internal class AtomicWriteTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val dir = "/state".toPath()
    private val target = dir / "file.bin"
    private val staleTmp = dir / "file.bin$ATOMIC_WRITE_TMP_SUFFIX"

    @Before
    fun setUp() {
        fsRule.fileSystem.createDirectories(dir)
    }

    @Test
    fun `write creates the target`() {
        fsRule.fileSystem.writeAtomically(target) { it.writeUtf8("hello") }
        assertEquals("hello", read(target))
        assertEquals(listOf(target), fsRule.fileSystem.list(dir))
    }

    @Test
    fun `write replaces existing content`() {
        fsRule.fileSystem.write(target) { writeUtf8("a much longer original value") }
        fsRule.fileSystem.writeAtomically(target) { it.writeUtf8("new") }
        assertEquals("new", read(target))
        assertEquals(listOf(target), fsRule.fileSystem.list(dir))
    }

    @Test
    fun `each write creates a fresh temp file`() {
        val tmps = mutableListOf<Path>()
        val recording = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
                assertTrue(mustCreate)
                tmps += file
                return super.openReadWrite(file, mustCreate, mustExist)
            }
        }
        val random = Random(SEED)
        recording.writeAtomically(target, random) { it.writeUtf8("a") }
        recording.writeAtomically(target, random) { it.writeUtf8("b") }

        val expected = Random(SEED).let { seeded ->
            List(2) { dir / "file.bin.${seeded.nextLong().toULong().toString(16)}$ATOMIC_WRITE_TMP_SUFFIX" }
        }
        assertEquals(expected, tmps)
        assertNotEquals(tmps[0], tmps[1])
        assertEquals("b", read(target))
    }

    @Test
    fun `stale temp file is not reused`() {
        fsRule.fileSystem.write(staleTmp) { writeUtf8("leftover from a previous crash") }
        fsRule.fileSystem.writeAtomically(target) {
            // a sweep deleting the stale temp file mid-write must not affect this write
            fsRule.fileSystem.delete(staleTmp)
            it.writeUtf8("new")
        }
        assertEquals("new", read(target))
        assertEquals(listOf(target), fsRule.fileSystem.list(dir))
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
        assertEquals(listOf(target), fsRule.fileSystem.list(dir))
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
        assertEquals(listOf(target), fsRule.fileSystem.list(dir))
    }

    private fun read(path: Path): String = fsRule.fileSystem.read(path) { readUtf8() }

    private companion object {
        const val SEED = 42
    }
}
