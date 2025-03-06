package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.atomic.AtomicReference

class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    anrMonitoringThread: Thread? = null,
    private val testWorker: Worker? = null,
    private val anotherTestWorker: Worker? = null,
    private val testPriorityWorker: Worker.Priority? = null,
    private val base: WorkerThreadModule = createWorkerThreadModule(),
) : WorkerThreadModule by base {

    val executorClock: FakeClock = fakeInitModule.getFakeClock() ?: FakeClock()
    val executor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)
    val anotherExecutor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)
    val priorityWorkerExecutor: BlockingScheduledExecutorService =
        BlockingScheduledExecutorService(fakeClock = executorClock)

    private val backgroundWorker = BackgroundWorker(executor)
    private val anotherBackgroundWorker = BackgroundWorker(anotherExecutor)
    private val priorityWorker = PriorityWorker<Any>(priorityWorkerExecutor)
    private val anrMonitoringThreadRef: AtomicReference<Thread> = if (anrMonitoringThread == null) {
        base.anrMonitorThread
    } else {
        AtomicReference(anrMonitoringThread)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> =
        if (worker == testPriorityWorker) {
            priorityWorker as PriorityWorker<T>
        } else {
            base.priorityWorker(worker)
        }

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker {
        return when (worker) {
            testWorker -> backgroundWorker
            anotherTestWorker -> anotherBackgroundWorker
            else -> base.backgroundWorker(worker)
        }
    }

    override val anrMonitorThread: AtomicReference<Thread> = anrMonitoringThreadRef
}
