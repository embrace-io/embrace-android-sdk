package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import java.util.concurrent.ScheduledExecutorService

internal class FakeWorkerThreadModule(
    fakeClock: FakeClock,
    private val name: ExecutorName,
    private val base: WorkerThreadModule = WorkerThreadModuleImpl()
) : WorkerThreadModule by base {

    private val executor = BlockingScheduledExecutorService(fakeClock)

    override fun scheduledExecutor(executorName: ExecutorName): ScheduledExecutorService {
        return when (executorName) {
            name -> executor
            else -> base.scheduledExecutor(executorName)
        }
    }
}
