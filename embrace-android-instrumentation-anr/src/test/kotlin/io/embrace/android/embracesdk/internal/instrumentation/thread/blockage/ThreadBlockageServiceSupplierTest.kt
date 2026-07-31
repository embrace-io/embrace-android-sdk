package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeThreadBlockageListener
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

private const val BASELINE_MS = 15020000L
private const val BLOCKAGE_MS = 2000L

@RunWith(AndroidJUnit4::class)
internal class ThreadBlockageServiceSupplierTest {

    @Test
    fun testDefaultImplementations() {
        assertNotNull(createService(FakeConfigService()))
    }

    @Test
    fun testBehaviorDisabled() {
        assertNull(createService(configService(threadBlockage = false, responsiveness = false)))
    }

    @Test
    fun `service is created when only responsiveness capture is enabled`() {
        assertNotNull(createService(configService(threadBlockage = false, responsiveness = true)))
    }

    @Test
    fun `service is created when only thread blockage capture is enabled`() {
        assertNotNull(createService(configService(threadBlockage = true, responsiveness = false)))
    }

    @Test
    fun `the stacktrace sampler is registered when thread blockage capture is enabled`() {
        assertEquals(1, spansAfterOneBlockage(threadBlockage = true, responsiveness = false))
    }

    @Test
    fun `the stacktrace sampler is not registered when only responsiveness capture is enabled`() {
        // the sampler exists but was never told about the blockage, so there is nothing to report
        assertEquals(0, spansAfterOneBlockage(threadBlockage = false, responsiveness = true))
    }

    /**
     * Runs the detector until it reports one complete blockage, then returns the number of thread
     * blockage spans the service has to report. That is non-zero only if the supplier registered the
     * stacktrace sampler.
     */
    private fun spansAfterOneBlockage(threadBlockage: Boolean, responsiveness: Boolean): Int {
        val clock = FakeClock(BASELINE_MS)
        val watchdogExecutorService = BlockingScheduledExecutorService(clock)
        val service = checkNotNull(
            createService(
                configService = configService(threadBlockage, responsiveness),
                clock = clock,
                watchdogWorker = BackgroundWorker(watchdogExecutorService),
            ),
        )

        // an independent listener, so a drive that fails to provoke a blockage fails the test rather
        // than looking like an unregistered sampler
        val observer = FakeThreadBlockageListener()
        service.addListener(observer)

        service.startCapture()
        watchdogExecutorService.runCurrentlyBlocked()
        watchdogExecutorService.moveForwardAndRunBlocked(BLOCKAGE_MS)
        service.simulateTargetThreadResponse()

        assertEquals(BLOCKAGE_MS, observer.ended.single().durationMs)
        return service.snapshotSpans().size
    }

    private fun createService(
        configService: FakeConfigService,
        clock: FakeClock = FakeClock(),
        watchdogWorker: BackgroundWorker? = null,
    ): ThreadBlockageService? {
        val application = ApplicationProvider.getApplicationContext<Application>()
        return createThreadBlockageService(
            FakeInstrumentationArgs(
                application,
                configService = configService,
                clock = clock,
                backgroundWorkerSupplier = { watchdogWorker ?: BackgroundWorker(BlockingScheduledExecutorService(clock)) },
            ),
        )
    }

    private fun configService(threadBlockage: Boolean, responsiveness: Boolean) = FakeConfigService(
        autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
            threadBlockageServiceEnabled = threadBlockage,
            responsivenessCaptureEnabled = responsiveness,
        ),
    )
}
