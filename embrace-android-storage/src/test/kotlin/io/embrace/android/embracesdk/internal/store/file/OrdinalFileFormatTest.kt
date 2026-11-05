package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.fs.fakeFileSystem
import io.embrace.android.embracesdk.internal.store.Ordinal
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrdinalFileFormatTest {

    private val path = "/ordinals.bin".toPath()
    private lateinit var fs: FakeFileSystem

    @Before
    fun setUp() {
        fs = fakeFileSystem()
    }

    @After
    fun tearDown() {
        fs.checkNoOpenFiles()
    }

    @Test
    fun `every ordinal has its own slot except user session`() {
        val offsets = Ordinal.entries.filter { it != Ordinal.USER_SESSION }.map(OrdinalFileFormat::slotOffset)
        assertEquals(offsets.size, offsets.toSet().size)
        assertTrue(offsets.all { it >= 8 && it + Int.SIZE_BYTES <= OrdinalFileFormat.MAPPED_BYTES })
        assertEquals(OrdinalFileFormat.slotOffset(Ordinal.SESSION), OrdinalFileFormat.slotOffset(Ordinal.USER_SESSION))
    }

    @Test
    fun `prepare leaves a complete file unchanged`() {
        fs.write(path) { OrdinalFileFormat.write(this, mapOf(Ordinal.SESSION to 5, Ordinal.CRASH to 2)) }
        val before = fs.read(path) { readByteArray() }
        prepare()
        assertArrayEquals(before, fs.read(path) { readByteArray() })
        assertArrayEquals(ordinalFile(2, -1, -1, 5, -1, -1), before)
    }

    @Test
    fun `write rejects user session`() {
        assertThrows(IllegalArgumentException::class.java) {
            fs.write(path) { OrdinalFileFormat.write(this, mapOf(Ordinal.USER_SESSION to 1)) }
        }
    }

    @Test
    fun `short file is extended with unset slots`() {
        fs.write(path) { write(ordinalFile(9, 8)) }
        prepare()
        assertArrayEquals(ordinalFile(9, 8, -1, -1, -1, -1), fs.read(path) { readByteArray() })
    }

    @Test
    fun `partial trailing slot is overwritten as unset`() {
        fs.write(path) { write(ordinalFile(9) + byteArrayOf(0, 0)) }
        prepare()
        assertArrayEquals(ordinalFile(9, -1, -1, -1, -1, -1), fs.read(path) { readByteArray() })
    }

    @Test
    fun `newer version with extra slots is untouched`() {
        val newer = ordinalFile(1, 2, 3, 4, 5, 6, 77, 88, version = 2)
        fs.write(path) { write(newer) }
        prepare()
        assertArrayEquals(newer, fs.read(path) { readByteArray() })
    }

    @Test
    fun `bad magic throws`() {
        assertPrepareThrows(ordinalFile(1, 2, 3, 4, 5, 6, magic = 0x12345678))
    }

    @Test
    fun `truncated header throws`() {
        assertPrepareThrows(ordinalFile().copyOf(4))
    }

    @Test
    fun `older version throws`() {
        assertPrepareThrows(ordinalFile(version = 0))
    }

    private fun assertPrepareThrows(bytes: ByteArray) {
        fs.write(path) { write(bytes) }
        assertThrows(IOException::class.java) { prepare() }
        assertArrayEquals(bytes, fs.read(path) { readByteArray() })
    }

    private fun prepare() = fs.openReadWrite(path).use(OrdinalFileFormat::prepare)
}
