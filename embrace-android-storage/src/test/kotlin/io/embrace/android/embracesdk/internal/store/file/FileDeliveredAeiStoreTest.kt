package io.embrace.android.embracesdk.internal.store.file

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoWriter
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class FileDeliveredAeiStoreTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val path = "/install_state/aei_delivered.bin".toPath()
    private val executor = BlockingScheduledExecutorService(blockingMode = true)
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private var writes = 0
    private var failWrites = false

    @Before
    fun setUp() {
        fsRule.fileSystem.createDirectories(path.parent!!)
    }

    @Test
    fun `missing file reads as empty without writing`() {
        val store = createStore()
        assertEquals(emptySet<String>(), store.ids())
        store.retainOnly(setOf("a"))
        executor.runCurrentlyBlocked()
        assertEquals(0, writes)
        assertEquals(false, fsRule.fileSystem.exists(path))
    }

    @Test
    fun `a pass of changes is coalesced into one write and read back`() {
        createStore().apply {
            add("a")
            add("b")
            add("c")
        }
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)

        val store = createStore()
        assertEquals(setOf("a", "b", "c"), store.ids())
        store.retainOnly(setOf("a", "c", "d"))
        store.add("d")
        executor.runCurrentlyBlocked()
        assertEquals(2, writes)
        assertEquals(setOf("a", "c", "d"), createStore().ids())
    }

    @Test
    fun `unchanged set is not rewritten`() {
        createStore().add("a")
        executor.runCurrentlyBlocked()

        createStore().apply {
            add("a")
            retainOnly(setOf("a", "b"))
        }
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
    }

    @Test
    fun `newer file keeps known IDs and its unknown fields survive a rewrite`() {
        val newer = Buffer().also { DeliveredAeiCodec.encode(DeliveredAeiProto(ids = listOf("a", "b")), it) }
        ProtoAdapter.STRING.encodeWithTag(ProtoWriter(newer), 100, "from a newer SDK")
        val unknownFields = DeliveredAeiCodec.decode(newer.copy()).unknownFields
        fsRule.fileSystem.write(path) { writeAll(newer) }

        val store = createStore()
        assertEquals(setOf("a", "b"), store.ids())
        store.retainOnly(setOf("b"))
        executor.runCurrentlyBlocked()

        val rewritten = fsRule.fileSystem.read(path) { DeliveredAeiCodec.decode(this) }
        assertEquals(listOf("b"), rewritten.ids)
        assertEquals(unknownFields, rewritten.unknownFields)
    }

    @Test
    fun `corrupt file reads as empty and tracks an error`() {
        fsRule.fileSystem.write(path) { writeUtf8("garbage") }
        assertEquals(emptySet<String>(), createStore().ids())
        assertEquals("DeliveredAeiLoadFail", errorHandler.sdkErrorMessages.single().msg)
    }

    @Test
    fun `failed write keeps the previous file`() {
        createStore().add("kept")
        executor.runCurrentlyBlocked()
        failWrites = true
        createStore().add("lost")
        executor.runCurrentlyBlocked()
        failWrites = false

        assertEquals("DeliveredAeiWriteFail", errorHandler.sdkErrorMessages.single().msg)
        assertEquals(setOf("kept"), createStore().ids())
    }

    @Test
    fun `codec rejects bad bytes`() {
        val full = Buffer().also { DeliveredAeiCodec.encode(DeliveredAeiProto(ids = listOf("1000_123")), it) }
        listOf(
            Buffer(),
            Buffer().write("PSDK".encodeUtf8()),
            Buffer().write(full.readByteString(full.size - 2)),
        ).forEach { assertThrows(IOException::class.java) { DeliveredAeiCodec.decode(it) } }
    }

    private fun createStore() = FileDeliveredAeiStore(
        fileSystem = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (failWrites) throw IOException("rename failed")
                writes++
                super.atomicMove(source, target)
            }
        },
        path = path,
        worker = BackgroundWorker(executor),
        errorHandler = errorHandler,
    )
}
