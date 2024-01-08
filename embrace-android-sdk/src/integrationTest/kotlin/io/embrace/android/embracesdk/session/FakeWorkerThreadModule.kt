package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl

internal class FakeWorkerThreadModule(
    fakeClock: FakeClock,
    private val name: WorkerName,
    private val base: WorkerThreadModule = WorkerThreadModuleImpl()
) : WorkerThreadModule by base {

    val executor = BlockingScheduledExecutorService(fakeClock)

    private val worker = ScheduledWorker(executor)

    override fun scheduledWorker(workerName: WorkerName): ScheduledWorker {
        return when (workerName) {
            name -> worker
            else -> base.scheduledWorker(workerName)
        }
    }
}
