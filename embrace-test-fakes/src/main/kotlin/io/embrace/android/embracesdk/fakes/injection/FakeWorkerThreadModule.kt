package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.atomic.AtomicReference

class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    private val testWorkerName: Worker? = null,
    private val anotherTestWorkerName: Worker? = null,
    private val base: WorkerThreadModule = createWorkerThreadModule(fakeInitModule)
) : WorkerThreadModule by base {

    val executorClock: FakeClock = fakeInitModule.getFakeClock() ?: FakeClock()
    val executor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)
    val anotherExecutor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)

    private val backgroundWorker = BackgroundWorker(executor)
    private val anotherBackgroundWorker = BackgroundWorker(anotherExecutor)

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker {
        return when (worker) {
            testWorkerName -> backgroundWorker
            anotherTestWorkerName -> anotherBackgroundWorker
            else -> base.backgroundWorker(worker)
        }
    }

    override var anrMonitorThread: AtomicReference<Thread> = base.anrMonitorThread
}
