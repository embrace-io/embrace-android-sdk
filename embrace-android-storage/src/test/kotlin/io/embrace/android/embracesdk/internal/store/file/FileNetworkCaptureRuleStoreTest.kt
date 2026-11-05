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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class FileNetworkCaptureRuleStoreTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val path = "/install_state/network_capture.bin".toPath()
    private val executor = BlockingScheduledExecutorService(blockingMode = true)
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private var liveRuleIds = setOf("a", "b")
    private var writes = 0
    private var failWrites = false

    @Before
    fun setUp() {
        fsRule.fileSystem.createDirectories(path.parent!!)
    }

    @Test
    fun `missing file reads as the max count without writing`() {
        assertEquals(5, createStore().remaining("a", 5))
        executor.runCurrentlyBlocked()
        assertEquals(0, writes)
        assertFalse(fsRule.fileSystem.exists(path))
    }

    @Test
    fun `decrement survives reopen`() {
        createStore().apply {
            decrement("a", 5)
            decrement("b", 5)
            decrement("b", 5)
        }
        executor.runCurrentlyBlocked()

        val store = createStore()
        assertEquals(4, store.remaining("a", 5))
        assertEquals(3, store.remaining("b", 5))
        assertEquals(mapOf("a" to 1, "b" to 2), readFile().used)
    }

    @Test
    fun `decrements before a write are coalesced into one write`() {
        val store = createStore()
        repeat(100) { store.decrement("a", 1000) }
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
        assertEquals(900, createStore().remaining("a", 1000))
    }

    @Test
    fun `remaining follows a change to the max count`() {
        createStore().apply {
            decrement("a", 2)
            decrement("a", 2)
        }
        executor.runCurrentlyBlocked()

        val store = createStore()
        assertEquals(0, store.remaining("a", 2))
        assertEquals(0, store.remaining("a", 1))
        assertEquals(3, store.remaining("a", 5))
    }

    @Test
    fun `decrementing an exhausted rule does not count or write`() {
        createStore().decrement("a", 1)
        executor.runCurrentlyBlocked()

        val store = createStore()
        store.decrement("a", 1)
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
        assertEquals(1, store.remaining("a", 2))
    }

    @Test
    fun `rules that are no longer live are dropped on write`() {
        createStore().apply {
            decrement("a", 5)
            decrement("b", 5)
        }
        executor.runCurrentlyBlocked()

        liveRuleIds = setOf("b", "c")
        val store = createStore()
        assertEquals(4, store.remaining("a", 5))
        store.decrement("c", 5)
        executor.runCurrentlyBlocked()

        assertEquals(mapOf("b" to 1, "c" to 1), readFile().used)
        assertEquals(5, store.remaining("a", 5))
    }

    @Test
    fun `newer file is read without being rewritten and its unknown fields survive a rewrite`() {
        val newer = Buffer().also { NetworkCaptureCodec.encode(NetworkCaptureProto(used = mapOf("a" to 2)), it) }
        ProtoAdapter.STRING.encodeWithTag(ProtoWriter(newer), 100, "from a newer SDK")
        val unknownFields = NetworkCaptureCodec.decode(newer.copy()).unknownFields
        fsRule.fileSystem.write(path) { writeAll(newer.copy()) }

        val store = createStore()
        assertEquals(3, store.remaining("a", 5))
        executor.runCurrentlyBlocked()
        assertEquals(0, writes)

        store.decrement("b", 5)
        executor.runCurrentlyBlocked()
        val rewritten = readFile()
        assertEquals(mapOf("a" to 2, "b" to 1), rewritten.used)
        assertEquals(unknownFields, rewritten.unknownFields)
    }

    @Test
    fun `corrupt file reads as empty and tracks an error`() {
        fsRule.fileSystem.write(path) { writeUtf8("garbage") }
        assertEquals(5, createStore().remaining("a", 5))
        assertEquals("NetworkCaptureLoadFail", errorHandler.sdkErrorMessages.single().msg)
    }

    @Test
    fun `failed write keeps the previous file`() {
        createStore().decrement("a", 5)
        executor.runCurrentlyBlocked()
        failWrites = true
        createStore().decrement("b", 5)
        executor.runCurrentlyBlocked()
        failWrites = false

        assertEquals("NetworkCaptureWriteFail", errorHandler.sdkErrorMessages.single().msg)
        assertEquals(mapOf("a" to 1), readFile().used)
    }

    @Test
    fun `codec round trips`() {
        val proto = NetworkCaptureProto(used = mapOf("a" to 1, "b" to 3))
        val buffer = Buffer().also { NetworkCaptureCodec.encode(proto, it) }
        assertEquals(proto, NetworkCaptureCodec.decode(buffer))
    }

    @Test
    fun `codec rejects bad bytes`() {
        val full = Buffer().also { NetworkCaptureCodec.encode(NetworkCaptureProto(used = mapOf("rule" to 1)), it) }
        listOf(
            Buffer(),
            Buffer().write("AEID".encodeUtf8()),
            Buffer().write(full.readByteString(full.size - 2)),
        ).forEach { assertThrows(IOException::class.java) { NetworkCaptureCodec.decode(it) } }
    }

    private fun readFile() = fsRule.fileSystem.read(path) { NetworkCaptureCodec.decode(this) }

    private fun createStore() = FileNetworkCaptureRuleStore(
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
        liveRuleIds = { liveRuleIds },
    )
}
