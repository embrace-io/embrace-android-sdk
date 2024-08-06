package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.embrace.android.embracesdk.internal.worker.WorkerName
import java.util.concurrent.atomic.AtomicReference

public class FakeWorkerThreadModule(
    fakeInitModule: FakeInitModule = FakeInitModule(),
    private val name: WorkerName? = null,
    private val base: WorkerThreadModule = WorkerThreadModuleImpl(fakeInitModule)
) : WorkerThreadModule by base {

    public val executorClock: FakeClock = fakeInitModule.getFakeClock() ?: FakeClock()
    public val executor: BlockingScheduledExecutorService = BlockingScheduledExecutorService(fakeClock = executorClock)

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

    override var anrMonitorThread: AtomicReference<Thread> = base.anrMonitorThread
}
