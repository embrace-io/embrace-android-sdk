package io.embrace.android.embracesdk.internal.session.caching

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class PeriodicBackgroundActivityCacherTest {

    private val clock: FakeClock = FakeClock()
    private val logger: EmbLogger = FakeEmbLogger()
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var worker: BackgroundWorker
    private lateinit var cacher: PeriodicBackgroundActivityCacher
    private lateinit var executionCount: AtomicInteger

    @Before
    fun setUp() {
        executor = BlockingScheduledExecutorService(
            fakeClock = clock,
            blockingMode = true
        )
        worker = BackgroundWorker(executor)
        cacher = PeriodicBackgroundActivityCacher(
            clock = clock,
            logger = logger,
            worker = worker
        )
        executionCount = AtomicInteger(0)
    }

    @Test
    fun `do not save more than once if delay time has not elapsed`() {
        queueScheduleSave()
        queueScheduleSave()
        val latch = queueCompletionTask()
        executor.blockingMode = false
        latch.assertCountedDown()
        assertEquals(1, executionCount.get())
    }

    @Test
    fun `scheduled save will run once minimum delay time has elapsed`() {
        val latch1 = queueScheduleSave()
        executor.runCurrentlyBlocked()
        latch1.assertCountedDown()
        assertEquals(0, executor.scheduledTasksCount())
        val latch2 = queueScheduleSave()
        assertEquals(1, executor.scheduledTasksCount())
        executor.moveForwardAndRunBlocked(1999)
        executor.blockingMode = false
        queueCompletionTask().assertCountedDown()
        assertEquals(1, latch2.count)
        assertEquals(1, executionCount.get())
        executor.moveForwardAndRunBlocked(2)
        latch2.assertCountedDown()
        assertEquals(2, executionCount.get())
    }

    @Test
    fun `stopping cacher prevents execution of the pending scheduled save`() {
        queueScheduleSave()
        cacher.stop()
        val latch = queueCompletionTask()
        executor.blockingMode = false
        latch.assertCountedDown()
        assertEquals(0, executionCount.get())
    }

    private fun queueScheduleSave(): CountDownLatch {
        val latch = CountDownLatch(1)
        cacher.scheduleSave {
            executionCount.incrementAndGet()
            latch.countDown()
            fakeSessionEnvelope()
        }
        return latch
    }

    private fun queueCompletionTask(): CountDownLatch {
        val latch = CountDownLatch(1)
        executor.submit {
            latch.countDown()
        }
        return latch
    }

    private fun CountDownLatch.assertCountedDown() {
        await(1, TimeUnit.SECONDS)
        assertEquals(0, count)
    }
}
