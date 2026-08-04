package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.assertions.assertCountedDown
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeRemoteConfigSource
import io.embrace.android.embracesdk.fakes.FakeRemoteConfigStore
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.StoredConfigResponse
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor

class CombinedRemoteConfigSourceTest {

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
    }

    @Test
    fun `test requests scheduled`() {
        val source = createSource(response = null)
        assertEquals(0, remoteConfigSource.callCount)
        source.scheduleConfigRequests()
        executorService.runCurrentlyBlocked()
        assertEquals(1, remoteConfigSource.callCount)
        assertNull(remoteConfigSource.etag)
    }

    @Test
    fun `test persisted etag value populated`() {
        val source = createSource(response = StoredConfigResponse(RemoteConfig(), "etag", null))
        assertEquals(0, remoteConfigSource.callCount)
        source.scheduleConfigRequests()
        executorService.runCurrentlyBlocked()
        assertEquals("etag", remoteConfigSource.etag)
        assertEquals(1, remoteConfigSource.callCount)
        assertEquals(1, remoteConfigStore.saveCount)
    }

    @Test
    fun `a throwing request does not stop the recurring schedule`() {
        val worker = BackgroundWorker(ScheduledThreadPoolExecutor(1))
        val latch = CountDownLatch(3)
        remoteConfigSource.onCall = {
            latch.countDown()
            if (remoteConfigSource.callCount == 1) {
                throw SerializationException("schema mismatch")
            }
        }

        try {
            createSource(response = null, worker = worker, intervalMs = 1).scheduleConfigRequests()
            latch.assertCountedDown(waitTimeMs = 5000)
        } finally {
            worker.shutdownAndWait(timeoutMs = 1000)
        }

        // the config that arrived after the failure was still persisted
        assertTrue("expected at least one config to be saved", remoteConfigStore.saveCount > 0)
    }

    private fun createSource(
        response: StoredConfigResponse?,
        worker: BackgroundWorker = BackgroundWorker(executorService),
        intervalMs: Long = 60 * 60 * 1000,
    ) = CombinedRemoteConfigSource(
        remoteConfigStore,
        response,
        lazy { remoteConfigSource },
        worker,
        intervalMs,
    )
}
