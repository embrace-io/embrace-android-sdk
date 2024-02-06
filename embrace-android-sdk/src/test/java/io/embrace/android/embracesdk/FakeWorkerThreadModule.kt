package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2

/**
 * Version of [WorkerThreadModule] used for tests that uses and exposes [BlockableExecutorService] and [BlockingScheduledExecutorService]
 * so test writers can control the execution of jobs on these threads.
 *
 * Note: the providers must return the Blocking versions of the executor services that [WorkerThreadModule] relies on, so the overridden
 * methods that provide access to those executor services can also explicitly return those types. Be aware of this when using this fake.
 */
internal class FakeWorkerThreadModule(
    executorProvider: KFunction1<Boolean, BlockableExecutorService> = ::BlockableExecutorService,
    scheduledExecutorProvider: KFunction2<FakeClock, Boolean, BlockingScheduledExecutorService> = ::BlockingScheduledExecutorService,
    private val clock: FakeClock = FakeClock(),
    private val blockingMode: Boolean = false
) : WorkerThreadModule {
    private val executorServices =
        WorkerName.values().associateWith {
            executorProvider(blockingMode)
        }

    private val scheduledExecutorServices =
        WorkerName.values().associateWith {
            scheduledExecutorProvider(clock, blockingMode)
        }

    override fun backgroundWorker(workerName: WorkerName): BackgroundWorker {
        return BackgroundWorker(checkNotNull(executorServices[workerName]))
    }

    override fun scheduledWorker(workerName: WorkerName): ScheduledWorker {
        return ScheduledWorker(checkNotNull(scheduledExecutorServices[workerName]))
    }

    fun executor(workerName: WorkerName): BlockableExecutorService {
        return checkNotNull(executorServices[workerName])
    }

    fun scheduledExecutor(workerName: WorkerName): BlockingScheduledExecutorService {
        return checkNotNull(scheduledExecutorServices[workerName])
    }

    override val anrMonitorThread: AtomicReference<Thread> = AtomicReference(Thread.currentThread())

    override fun close() {
        executorServices.values.forEach { it.shutdown() }
        scheduledExecutorServices.values.forEach { it.shutdown() }
    }
}
