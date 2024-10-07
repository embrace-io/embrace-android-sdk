package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

private const val INTERVAL = 1L

class PayloadCachingServiceImplTest {

    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var service: PayloadCachingService

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService(FakeClock())
        val cacher = PeriodicSessionCacher(BackgroundWorker(executorService), FakeEmbLogger(), INTERVAL)
        service = PayloadCachingServiceImpl(cacher)
    }

    @Test(expected = RejectedExecutionException::class)
    fun rejection() {
        service.shutdown()
        service.startCaching(ProcessState.FOREGROUND) { null }
    }

    @Test
    fun `start caching`() {
        var count = 0
        service.startCaching(ProcessState.FOREGROUND) {
            count++
            fakeSessionEnvelope()
        }
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(1, count)
    }

    @Test
    fun `stop caching`() {
        var count = 0
        service.startCaching(ProcessState.FOREGROUND) {
            count++
            fakeSessionEnvelope()
        }
        service.stopCaching()
        executorService.moveForwardAndRunBlocked(INTERVAL)
        assertEquals(0, count)
    }
}
