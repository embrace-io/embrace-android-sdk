package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeRemoteConfigSource
import io.embrace.android.embracesdk.fakes.FakeRemoteConfigStore
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CombinedRemoteConfigSourceTest {

    private lateinit var source: CombinedRemoteConfigSource
    private lateinit var remoteConfig: RemoteConfig
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var remoteConfigSource: FakeRemoteConfigSource
    private lateinit var remoteConfigStore: FakeRemoteConfigStore

    @Before
    fun setUp() {
        remoteConfig = RemoteConfig(92)
        executorService = BlockingScheduledExecutorService()
        remoteConfigSource = FakeRemoteConfigSource(ConfigHttpResponse(remoteConfig, "another"))
        remoteConfigStore = FakeRemoteConfigStore()
        source = CombinedRemoteConfigSource(
            remoteConfigStore,
            lazy { remoteConfigSource },
            BackgroundWorker(executorService)
        )
    }

    @Test
    fun `test initial config null`() {
        assertNull(source.getConfig())
    }

    @Test
    fun `test initial config populated`() {
        val cfg = RemoteConfig(100)
        source = CombinedRemoteConfigSource(
            FakeRemoteConfigStore(ConfigHttpResponse(cfg, null)),
            lazy { remoteConfigSource },
            BackgroundWorker(executorService)
        )
        assertEquals(cfg, source.getConfig())
    }

    @Test
    fun `test requests scheduled`() {
        assertEquals(0, remoteConfigSource.callCount)
        source.scheduleConfigRequests()
        executorService.runCurrentlyBlocked()
        assertEquals(1, remoteConfigSource.callCount)
        assertNull(remoteConfigSource.etag)
    }

    @Test
    fun `test persisted etag value populated`() {
        remoteConfigStore.impl = ConfigHttpResponse(RemoteConfig(), "etag")
        assertEquals(0, remoteConfigSource.callCount)
        source.scheduleConfigRequests()
        executorService.runCurrentlyBlocked()
        assertEquals("etag", remoteConfigSource.etag)
        assertEquals(1, remoteConfigSource.callCount)
        assertEquals(1, remoteConfigStore.saveCount)
    }
}
