package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionIdProvider
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.AppState
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
    private lateinit var sessionIdProvider: FakeSessionIdProvider
    private val zygote = fakeSessionPartToken()

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService(FakeClock())
        val cacher = PeriodicSessionPartCacher(BackgroundWorker(executorService), FakeInternalLogger(), INTERVAL)
        sessionIdProvider = FakeSessionIdProvider(sessionPartId = zygote.sessionPartId)

        service = PayloadCachingServiceImpl(
            cacher,
            FakeClock(),
            sessionIdProvider,
            FakePayloadStore()
        )
    }

    @Test(expected = RejectedExecutionException::class)
    fun rejection() {
        service.shutdown()
        service.startCaching(zygote, AppState.FOREGROUND) { _, _, _ ->
            null
        }
    }

    @Test
    fun `session id mismatch does not cache`() {
        sessionIdProvider.sessionPartId = "someOtherId"
        var count = 0
        service.startCaching(zygote, AppState.FOREGROUND) { _, _, _ ->
            count++
            fakeSessionEnvelope()
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, count)
    }

    @Test
    fun `start caching`() {
        var count = 0
        service.startCaching(zygote, AppState.FOREGROUND) { _, _, _ ->
            count++
            fakeSessionEnvelope()
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(1, count)
    }

    @Test
    fun `stop caching`() {
        var count = 0
        service.startCaching(zygote, AppState.FOREGROUND) { _, _, _ ->
            count++
            fakeSessionEnvelope()
        }
        service.stopCaching()
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, count)
    }
}
