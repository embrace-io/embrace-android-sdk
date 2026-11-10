package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class FilePersistedSdkStateStoreTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val path = "/install_state/persisted_sdk_state.bin".toPath()
    private val executor = BlockingScheduledExecutorService(blockingMode = true)
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private var writes = 0
    private var failWrites = false

    @Before
    fun setUp() {
        fsRule.fileSystem.createDirectories(path.parent!!)
    }

    @Test
    fun `missing file creates and persists identity`() {
        val state = createStore().snapshot()
        assertEquals(PersistedSdkState(deviceId = "device-id", installTimestampMs = 5000L), state)
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
        assertEquals(state, createStore(uuid = "other").snapshot())
    }

    @Test
    fun `missing file is persisted by a worker that runs inline`() {
        executor.blockingMode = false
        val state = createStore().snapshot()
        assertEquals(1, writes)
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
        assertEquals(state, createStore(uuid = "other").snapshot())
    }

    @Test
    fun `updates are coalesced into one write and read back`() {
        val store = createStore()
        store.update { userId = "a" }
        store.update { sessionProperties = mapOf("k" to "v") }
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)

        val reread = createStore(uuid = "other")
        assertEquals(store.snapshot(), reread.snapshot())
        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
    }

    @Test
    fun `corrupt file reads as empty state and is replaced`() {
        fsRule.fileSystem.write(path) { writeUtf8("garbage") }
        val state = createStore().snapshot()
        assertEquals(PersistedSdkState(deviceId = "device-id", installTimestampMs = 5000L), state)
        assertEquals("PersistedSdkStateLoadFail", errorHandler.sdkErrorMessages.single().msg)

        executor.runCurrentlyBlocked()
        assertEquals(1, writes)
        assertEquals(state, createStore(uuid = "other").snapshot())
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `failed write keeps the previous file`() {
        createStore().update { userId = "kept" }
        executor.runCurrentlyBlocked()
        failWrites = true
        createStore().update { userId = "lost" }
        executor.runCurrentlyBlocked()
        failWrites = false

        assertEquals("PersistedSdkStateWriteFail", errorHandler.sdkErrorMessages.single().msg)
        assertEquals("kept", createStore().snapshot().userId)
    }

    @Test
    fun `created file is read back by a store`() {
        val state = PersistedSdkState(
            deviceId = "created-id",
            installTimestampMs = 1000L,
            userId = "user-id",
            userEmail = "user@example.com",
            userName = "user-name",
            userPersonas = setOf("persona"),
            sessionProperties = mapOf("key" to "value"),
            userSession = UserSessionMetadata.Classified(2000L, "session-id", 3L, 4L, 5L, 6, 7000L, true),
            hostedSdkVersion = "1.2.3",
            hostedPlatformVersion = "0.79.0",
            unityBuildIdNumber = "unity-build",
            javaScriptPatchNumber = "patch",
            rnBundleUrl = "assets://index.android.bundle",
            rnBundleId = "bundle-id",
            appVersionStartupScope = "2.1.0",
        )
        FilePersistedSdkStateStore.create(fsRule.fileSystem, path, state)

        assertEquals(state, createStore().snapshot())
        executor.runCurrentlyBlocked()
        assertEquals(0, writes)
    }

    private fun createStore(uuid: String = "device-id") = FilePersistedSdkStateStore(
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
        clock = FakeClock(currentTime = 5000L),
        uuidSource = { uuid },
    )
}
