package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.atomic.AtomicReference

class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    threadBlockageMonitoringThread: Thread? = null,
    testWorkers: List<Worker.Background> = emptyList(),
    private val testPriorityWorker: Worker.Priority? = null,
    private val base: WorkerThreadModule = WorkerThreadModuleImpl(),
) : WorkerThreadModule by base {

    val executorClock: FakeClock = fakeInitModule.getFakeClock() ?: FakeClock()
    val priorityWorkerExecutor: BlockingScheduledExecutorService =
        BlockingScheduledExecutorService(fakeClock = executorClock)

    private val workerExecutors: Map<Worker.Background, BlockingScheduledExecutorService> =
        testWorkers.associateWith { BlockingScheduledExecutorService(fakeClock = executorClock) }
    private val workers: Map<Worker.Background, BackgroundWorker> =
        workerExecutors.mapValues { (_, exec) -> BackgroundWorker(exec) }

    /** Returns the [BlockingScheduledExecutorService] for [worker]; throws if [worker] is not faked. */
    fun executorFor(worker: Worker.Background): BlockingScheduledExecutorService =
        checkNotNull(workerExecutors[worker]) { "Worker $worker is not faked." }

    private val priorityWorker = PriorityWorker<Any>(priorityWorkerExecutor)
    private val threadBlockageWatchdogThreadRef: AtomicReference<Thread> = if (threadBlockageMonitoringThread == null) {
        base.threadBlockageMonitorThread
    } else {
        AtomicReference(threadBlockageMonitoringThread)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> =
        if (worker == testPriorityWorker) {
            priorityWorker as PriorityWorker<T>
        } else {
            base.priorityWorker(worker)
        }

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker =
        workers[worker] ?: base.backgroundWorker(worker)

    override val threadBlockageMonitorThread: AtomicReference<Thread> = threadBlockageWatchdogThreadRef
}
