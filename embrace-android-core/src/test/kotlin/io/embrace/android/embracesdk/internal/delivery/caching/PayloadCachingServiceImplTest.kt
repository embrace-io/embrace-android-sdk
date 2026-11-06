package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

private const val INTERVAL = 1L

class PayloadCachingServiceImplTest {

    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var service: PayloadCachingService
    private lateinit var sessionIdsProvider: FakeSessionIdsProvider
    private lateinit var payloadStore: FakePayloadStore
    private val zygote = fakeSessionPartToken()

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService(FakeClock())
        val cacher = PeriodicSessionPartCacher(BackgroundWorker(executorService), FakeErrorHandler(), INTERVAL)
        sessionIdsProvider = FakeSessionIdsProvider(sessionPartId = zygote.sessionPartId)
        payloadStore = FakePayloadStore()

        service = PayloadCachingServiceImpl(
            cacher,
            FakeClock(),
            sessionIdsProvider,
            payloadStore,
        )
    }

    @Test(expected = RejectedExecutionException::class)
    fun rejection() {
        service.shutdown()
        service.startCaching(zygote, ProcessState.FOREGROUND) { _, _, _, _ -> }
    }

    @Test
    fun `session id mismatch does not cache`() {
        sessionIdsProvider.sessionPartId = "someOtherId"
        var count = 0
        service.startCaching(zygote, ProcessState.FOREGROUND) { _, _, _, onSnapshot ->
            count++
            onSnapshot(fakeSessionEnvelope())
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, count)
        assertEquals(0, payloadStore.cachedSessionPartPayloads.size)
    }

    @Test
    fun `a snapshot of a part that ended while it was built is not cached`() {
        service.startCaching(zygote, ProcessState.FOREGROUND) { _, _, _, onSnapshot ->
            sessionIdsProvider.sessionPartId = "next-part"
            onSnapshot(fakeSessionEnvelope())
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, payloadStore.cachedSessionPartPayloads.size)
    }

    @Test
    fun `start caching`() {
        var count = 0
        service.startCaching(zygote, ProcessState.FOREGROUND) { _, _, _, onSnapshot ->
            count++
            onSnapshot(fakeSessionEnvelope())
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(1, count)
        assertEquals(1, payloadStore.cachedSessionPartPayloads.size)
    }

    @Test
    fun `stop caching`() {
        var count = 0
        service.startCaching(zygote, ProcessState.FOREGROUND) { _, _, _, onSnapshot ->
            count++
            onSnapshot(fakeSessionEnvelope())
        }
        service.stopCaching()
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, count)
    }
}
