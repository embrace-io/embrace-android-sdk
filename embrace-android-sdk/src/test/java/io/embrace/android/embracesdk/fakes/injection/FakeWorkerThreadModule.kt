package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl

internal class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    private val name: WorkerName? = null,
    private val base: WorkerThreadModule = WorkerThreadModuleImpl(fakeInitModule)
) : WorkerThreadModule by base {

    val executorClock = fakeInitModule.getFakeClock() ?: FakeClock()
    val executor = BlockingScheduledExecutorService(fakeClock = executorClock)

    private val backgroundWorker = BackgroundWorker(executor)
    private val scheduledWorker = ScheduledWorker(executor)

    override fun backgroundWorker(workerName: WorkerName): BackgroundWorker {
        return when (workerName) {
            name -> backgroundWorker
            else -> base.backgroundWorker(workerName)
        }
    }

    override fun scheduledWorker(workerName: WorkerName): ScheduledWorker {
        return when (workerName) {
            name -> scheduledWorker
            else -> base.scheduledWorker(workerName)
        }
    }
}
