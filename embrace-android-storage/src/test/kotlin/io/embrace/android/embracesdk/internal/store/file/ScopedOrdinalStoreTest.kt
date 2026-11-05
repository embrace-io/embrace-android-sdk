package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.store.Ordinal
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

internal class ScopedOrdinalStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var file: File
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var state: FakePersistedSdkStateStore
    private lateinit var store: ScopedOrdinalStore

    @Before
    fun setUp() {
        file = File(tempFolder.root, "ordinals.bin")
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        state = FakePersistedSdkStateStore()
        MmapOrdinalStore.create(FileSystem.SYSTEM, file.toOkioPath(), emptyMap())
        store = reopen()
    }

    @Test
    fun `first scope seeds and is persisted`() {
        assertEquals(5, increment("1.0") { 5 })
        assertEquals("1.0", state.state.appVersionStartupScope)
        assertEquals(6, increment("1.0") { 50 })
        assertEquals(1, state.updateCount)
    }

    @Test
    fun `scope change reseeds through seed`() {
        assertEquals(1, increment("1.0"))
        assertEquals(2, increment("1.0"))
        assertEquals(10, increment("2.0") { 10 })
        assertEquals("2.0", state.state.appVersionStartupScope)
        assertEquals(11, increment("2.0"))
    }

    @Test
    fun `reverting the scope resets again`() {
        increment("1.0")
        increment("1.0")
        increment("2.0")
        assertEquals(1, increment("1.0"))
        assertEquals("1.0", state.state.appVersionStartupScope)
    }

    @Test
    fun `scope survives a reopen of both stores`() {
        increment("1.0")
        increment("1.0")
        val encoded = Buffer().also { PersistedSdkStateCodec.encode(state.state.toProto(PersistedSdkStateProto()), it) }
        state = FakePersistedSdkStateStore(
            PersistedSdkStateCodec.decode(encoded).toPersistedSdkState("fake-device-id", 0L),
        )
        store = reopen()
        assertEquals(3, increment("1.0"))
        assertEquals(0, state.updateCount)
    }

    @Test
    fun `concurrent scope changes reset once`() {
        increment("1.0")
        val results = ConcurrentLinkedQueue<Int>()
        List(THREADS) {
            thread { repeat(INCREMENTS) { results.add(increment("2.0") { 10 }) } }
        }.forEach(Thread::join)
        assertEquals((10 until 10 + THREADS * INCREMENTS).toList(), results.sorted())
        assertEquals(2, state.updateCount)
    }

    @Test
    fun `null and empty scopes are distinct`() {
        assertEquals(1, increment(""))
        assertEquals("", state.state.appVersionStartupScope)
        assertEquals(2, increment(""))
        assertEquals(-1, increment(null))
        assertEquals(3, increment(""))
    }

    @Test
    fun `missing scope returns -1 without changing anything`() {
        increment("1.0")
        assertEquals(-1, increment(null))
        assertEquals("1.0", state.state.appVersionStartupScope)
        assertEquals(2, increment("1.0"))
    }

    @Test
    fun `failed seed leaves the counter and scope unchanged`() {
        increment("1.0")
        assertEquals(-1, increment("2.0") { error("boom") })
        assertEquals(listOf(SdkErrorType.PayloadStorageFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
        assertEquals("1.0", state.state.appVersionStartupScope)
        assertEquals(2, increment("1.0"))
    }

    @Test
    fun `failed first seed stores no scope`() {
        assertEquals(-1, increment("1.0") { error("boom") })
        assertNull(state.state.appVersionStartupScope)
        assertEquals(0, state.updateCount)
    }

    @Test
    fun `unscoped ordinals ignore scope`() {
        assertEquals(1, store.incrementAndGet(Ordinal.CRASH, "1.0"))
        assertEquals(2, store.incrementAndGet(Ordinal.CRASH, "2.0"))
        assertEquals(3, store.incrementAndGet(Ordinal.CRASH))
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION, "1.0"))
        assertNull(state.state.appVersionStartupScope)
        assertEquals(0, state.updateCount)
    }

    private fun increment(scope: String?, seed: () -> Int = { 1 }): Int =
        store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, scope, seed)

    private fun reopen(): ScopedOrdinalStore =
        ScopedOrdinalStore(checkNotNull(MmapOrdinalStore.open(file, errorHandler)), state, errorHandler)

    private companion object {
        const val THREADS = 8
        const val INCREMENTS = 100
    }
}
