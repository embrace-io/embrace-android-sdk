package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.atomic.AtomicReference

class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    private val name: Worker? = null,
    private val base: WorkerThreadModule = createWorkerThreadModule(fakeInitModule)
) : WorkerThreadModule by base {

    val executorClock: FakeClock = fakeInitModule.getFakeClock() ?: FakeClock()
    val executor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)

    private val backgroundWorker = BackgroundWorker(executor)
    private val scheduledWorker = ScheduledWorker(executor)

    override fun backgroundWorker(worker: Worker): BackgroundWorker {
        return when (worker) {
            name -> backgroundWorker
            else -> base.backgroundWorker(worker)
        }
    }

    override fun scheduledWorker(worker: Worker): ScheduledWorker {
        return when (worker) {
            name -> scheduledWorker
            else -> base.scheduledWorker(worker)
        }
    }

    override var anrMonitorThread: AtomicReference<Thread> = base.anrMonitorThread
}
