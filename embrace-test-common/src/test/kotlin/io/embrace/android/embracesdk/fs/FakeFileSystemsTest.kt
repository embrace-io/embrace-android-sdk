package io.embrace.android.embracesdk.fs

import okio.Path.Companion.toPath
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class FakeFileSystemsTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    @Test
    fun `written file can be read back and no handles are left open`() {
        val fs = fakeFileSystem()
        val dir = "/data/files".toPath()
        fs.createDirectories(dir)
        val file = dir / "payload.txt"
        fs.write(file) { writeUtf8("hello") }
        assertEquals("hello", fs.read(file) { readUtf8() })
        fs.checkNoOpenFiles()
    }

    @Test
    fun `open file can be deleted as on unix`() {
        val fs = fakeFileSystem()
        val file = "/marker".toPath()
        fs.write(file) { writeUtf8("x") }

        val source = fs.source(file).buffer()
        fs.delete(file)
        source.close()
        assertFalse(fs.exists(file))
    }

    @Test
    fun `rule supplies a working file system`() {
        val file = "/state.bin".toPath()
        fsRule.fileSystem.write(file) { writeInt(42) }
        assertTrue(fsRule.fileSystem.exists(file))
    }

    @Test
    fun `rule fails a test that leaks an open file`() {
        val rule = FakeFileSystemRule()
        val leaky = object : Statement() {
            override fun evaluate() {
                rule.fileSystem.sink("/leak".toPath())
            }
        }
        assertThrows(IllegalStateException::class.java) {
            rule.apply(leaky, Description.EMPTY).evaluate()
        }
    }
}
