package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.concurrency.ExecutionCoordinator
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.injection.AnrModule
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SAMPLE_INTERVAL_MS = 1L

@RunWith(AndroidJUnit4::class)
internal class AnrFeatureTest {

    // alter the config service + ANR module to provide hooks for writing test cases.
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness().apply {
            fakeConfigService.anrBehavior = fakeAnrBehavior(remoteCfg = {
                // override the default sample interval to 1ms to speed up tests.
                AnrRemoteConfig(sampleIntervalMs = SAMPLE_INTERVAL_MS)
            })
            anrModule = DecoratedAnrModuleImpl(anrModule, fakeClock, workerThreadModule)
        }
    }

    @Test
    fun `calling endSession ends stateful session`() {
        with(testRule) {
            val testListener = (harness.anrModule as DecoratedAnrModuleImpl).testListener
            val startTime = harness.fakeClock.now()

            val message = harness.recordSession {
                ExecutionCoordinator(
                    ExecutionCoordinator.OperationWrapper(),
                    null
                ).executeOperations(
                    first = { testListener.latch.await(1, TimeUnit.SECONDS) },
                    second = { harness.fakeClock.tick(100) },
                    firstBlocksSecond = false,
                    completionTimeoutSecs = 10
                )
            }
            val anrIntervals = checkNotNull(message?.performanceInfo?.anrIntervals)

            val interval = anrIntervals.single()
            assertEquals(AnrInterval.Type.UI, interval.type)
            assertEquals(startTime, interval.startTime)
            val samples = checkNotNull(interval.anrSampleList?.samples)
            assertEquals(1, samples.size)
            assertNotNull(samples.single())
        }
    }
}

private class DecoratedAnrModuleImpl(
    impl: AnrModule,
    clock: FakeClock,
    workerModule: WorkerThreadModule
) : AnrModule by impl {

    override val anrService: AnrService by lazy {
        impl.anrService.apply {
            addBlockedThreadListener(testListener)
        }
    }

    val testListener: TestBlockedThreadListener by lazy {
        val anrMonitorWorker = workerModule.scheduledWorker(WorkerName.ANR_MONITOR)
        TestBlockedThreadListener(clock, anrMonitorWorker)
    }
}

private class TestBlockedThreadListener(
    private val clock: FakeClock,
    private val worker: ScheduledWorker
) : BlockedThreadListener {

    val latch = CountDownLatch(1)

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        thread.name
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        thread.name
        latch.countDown()
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        thread.name
    }
}
