package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.store.Ordinal
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.concurrent.thread

class MmapOrdinalStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var file: File
    private lateinit var errorHandler: FakeErrorHandler

    @Before
    fun setUp() {
        file = File(tempFolder.root, "ordinals.bin")
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
    }

    @Test
    fun `create makes the file through the injected file system`() {
        val unwritable = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
                throw IOException("disk full")
        }
        assertThrows(IOException::class.java) {
            MmapOrdinalStore.create(unwritable, file.toOkioPath(), emptyMap())
        }
        assertFalse(file.exists())
    }

    @Test
    fun `create rejects user session`() {
        assertThrows(IllegalArgumentException::class.java) {
            MmapOrdinalStore.create(FileSystem.SYSTEM, file.toOkioPath(), mapOf(Ordinal.USER_SESSION to 5))
        }
        assertFalse(file.exists())
    }

    @Test
    fun `create then open round trips initial values`() {
        val store = createAndOpen(mapOf(Ordinal.SESSION to 5, Ordinal.CRASH to 2, Ordinal.APP_VERSION_STARTUP to 3))
        assertEquals(listOf(file.name), tempFolder.root.list()?.toList())
        assertEquals(6, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(3, store.incrementAndGet(Ordinal.CRASH))
        assertEquals(1, store.incrementAndGet(Ordinal.NATIVE_CRASH))
        assertEquals(4, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0"))
        assertEquals(LENGTH, file.length())
    }

    @Test
    fun `create replaces the known slots and leaves newer ones alone`() {
        file.writeBytes(ordinalFile(5, 5, 5, 5, 5, 5, 77, 88))
        MmapOrdinalStore.create(FileSystem.SYSTEM, file.toOkioPath(), mapOf(Ordinal.CRASH to 4))
        assertArrayEquals(ordinalFile(4, -1, -1, -1, -1, -1, 77, 88), file.readBytes())

        assertTrue(file.delete())
        file.writeBytes(ordinalFile(1, 2, 3, 4, 5, 6, magic = 0x12345678))
        assertEquals(5, createAndOpen(mapOf(Ordinal.CRASH to 4)).incrementAndGet(Ordinal.CRASH))
        assertEquals(LENGTH, file.length())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `each ordinal writes only its own slot`() {
        val store = createAndOpen(emptyMap())
        assertEquals(10, store.incrementAndGet(Ordinal.CRASH) { 10 })
        assertEquals(20, store.incrementAndGet(Ordinal.NATIVE_CRASH) { 20 })
        assertEquals(30, store.incrementAndGet(Ordinal.AEI_CRASH) { 30 })
        assertEquals(40, store.incrementAndGet(Ordinal.SESSION) { 40 })
        assertEquals(50, store.incrementAndGet(Ordinal.SESSION_PART) { 50 })
        assertEquals(60, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0") { 60 })
        assertArrayEquals(ordinalFile(10, 20, 30, 40, 50, 60), file.readBytes())
    }

    @Test
    fun `increments survive reopen`() {
        val store = createAndOpen(emptyMap())
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(3, reopen().incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `seed is invoked for unset slots only`() {
        val store = createAndOpen(emptyMap())
        val seeds = mutableListOf(10, 50)
        assertEquals(10, store.incrementAndGet(Ordinal.SESSION_PART) { seeds.removeAt(0) })
        assertEquals(11, store.incrementAndGet(Ordinal.SESSION_PART) { seeds.removeAt(0) })
        assertEquals(listOf(50), seeds)
    }

    @Test
    fun `scope is ignored`() {
        val store = createAndOpen(mapOf(Ordinal.APP_VERSION_STARTUP to 7, Ordinal.CRASH to 1))
        assertEquals(8, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0"))
        assertEquals(9, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "2.0"))
        assertEquals(10, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP))
        assertEquals(2, store.incrementAndGet(Ordinal.CRASH, "other"))
    }

    @Test
    fun `user session shares the session slot`() {
        val store = createAndOpen(mapOf(Ordinal.SESSION to 4))
        assertEquals(5, store.incrementAndGet(Ordinal.USER_SESSION))
        assertEquals(6, store.incrementAndGet(Ordinal.SESSION))
    }

    @Test
    fun `failed seed returns -1 and leaves the slot unset`() {
        val store = createAndOpen(emptyMap())
        assertEquals(-1, store.incrementAndGet(Ordinal.SESSION_PART) { error("boom") })
        assertEquals(listOf(SdkErrorType.PayloadStorageFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `concurrent increments are not lost`() {
        val store = createAndOpen(emptyMap())
        List(THREADS) {
            thread { repeat(INCREMENTS) { store.incrementAndGet(Ordinal.SESSION_PART) } }
        }.forEach(Thread::join)
        assertEquals(THREADS * INCREMENTS + 1, store.incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `a new file or one without the magic opens with every ordinal unset`() {
        listOf(
            ByteArray(0),
            ByteArray(LENGTH.toInt()),
            ordinalFile(1, 2, 3, 4, 5, 6, magic = 0),
        ).forEach { bytes ->
            assertTrue(!file.exists() || file.delete())
            file.writeBytes(bytes)
            val store = reopen()
            Ordinal.entries.filter { it != Ordinal.USER_SESSION }.forEach {
                assertEquals(1, store.incrementAndGet(it))
            }
            assertArrayEquals(ordinalFile(1, 1, 1, 1, 1, 1), file.readBytes())
        }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a file with fewer slots is grown with unset slots, keeping its values`() {
        file.writeBytes(ordinalFile(9, 8))
        val store = reopen()
        assertEquals(10, store.incrementAndGet(Ordinal.CRASH))
        assertEquals(9, store.incrementAndGet(Ordinal.NATIVE_CRASH))
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        assertArrayEquals(ordinalFile(10, 9, -1, -1, 1, -1), file.readBytes())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `downgrade to an sdk with fewer ordinals leaves the newer slots alone`() {
        // Written by a hypothetical newer version that appended two ordinals after this version's six.
        file.writeBytes(ordinalFile(1, 2, 3, 4, 5, 6, 77, 88))

        val store = reopen()
        Ordinal.entries.filter { it != Ordinal.USER_SESSION }.forEach { store.incrementAndGet(it) }
        assertEquals(6, reopen().incrementAndGet(Ordinal.SESSION))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())

        // What the newer version reads back on re-upgrade: its extra slots untouched.
        assertArrayEquals(ordinalFile(2, 3, 4, 6, 6, 7, 77, 88), file.readBytes())
    }

    @Test
    fun `missing file returns null without an error`() {
        assertNull(MmapOrdinalStore.open(file, errorHandler))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `a bad magic is tracked and the slots reset in place`() {
        listOf(
            ordinalFile(1, 2, 3, 4, 5, 6, magic = 0x12345678),
            ByteArray(32) { 0x7f },
        ).forEach(::assertOpenRecovers)
    }

    @Test
    fun `unreadable file returns null and is left in place`() {
        assertTrue(file.mkdir())
        assertNull(MmapOrdinalStore.open(file, errorHandler))
        assertEquals(listOf(SdkErrorType.OrdinalStoreOpenFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
        assertTrue(file.isDirectory)
    }

    @Test
    fun `a staged store keeps its increments once committed`() {
        val staged = File(tempFolder.root, "ordinals.bin.staged.tmp")
        staged.writeBytes(ordinalFile(magic = 0x12345678))
        val initial = mapOf(Ordinal.SESSION to 5)
        val store = MmapOrdinalStore.stage(FileSystem.SYSTEM, staged.toOkioPath(), initial, errorHandler)
        assertEquals(6, store.incrementAndGet(Ordinal.SESSION))
        MmapOrdinalStore.commitStaged(FileSystem.SYSTEM, staged.toOkioPath(), file.toOkioPath())
        assertEquals(7, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(listOf(file.name), tempFolder.root.list()?.toList())
        assertEquals(8, reopen().incrementAndGet(Ordinal.SESSION))
    }

    private fun createAndOpen(initial: Map<Ordinal, Int>): MmapOrdinalStore {
        MmapOrdinalStore.create(FileSystem.SYSTEM, file.toOkioPath(), initial)
        return reopen()
    }

    private fun reopen(): MmapOrdinalStore = checkNotNull(MmapOrdinalStore.open(file, errorHandler))

    private fun assertOpenRecovers(bytes: ByteArray) {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        assertTrue(!file.exists() || file.delete())
        file.writeBytes(bytes)
        val store = reopen()
        assertEquals(listOf(SdkErrorType.OrdinalStoreOpenFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
        assertTrue(errorHandler.sdkErrorMessages.single().throwable is CorruptOrdinalFileException)
        Ordinal.entries.filter { it != Ordinal.USER_SESSION }.forEach {
            assertEquals(1, store.incrementAndGet(it))
        }
        assertEquals(listOf(file.name), tempFolder.root.list()?.toList())
        assertEquals(2, reopen().incrementAndGet(Ordinal.CRASH))
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    private companion object {
        const val LENGTH = 4L + 6 * 4
        const val THREADS = 8
        const val INCREMENTS = 1_000
    }
}
