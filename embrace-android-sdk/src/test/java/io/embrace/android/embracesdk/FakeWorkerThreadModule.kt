package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import kotlin.reflect.KFunction1

/**
 * Version of [WorkerThreadModule] used for tests that uses and exposes [BlockableExecutorService] and [BlockingScheduledExecutorService]
 * so test writers can control the execution of jobs on these threads.
 *
 * Note: the providers must return the Blocking versions of the executor services that [WorkerThreadModule] relies on, so the overridden
 * methods that provide access to those executor services can also explicitly return those types. Be aware of this when using this fake.
 */
internal class FakeWorkerThreadModule(
    executorProvider: KFunction1<Boolean, BlockableExecutorService> = ::BlockableExecutorService,
    scheduledExecutorProvider: KFunction1<FakeClock, BlockingScheduledExecutorService> = ::BlockingScheduledExecutorService,
    private val clock: FakeClock = FakeClock(),
    private val blockingMode: Boolean = false
) : WorkerThreadModule {
    private val executorServices =
        ExecutorName.values().associateWith {
            executorProvider(blockingMode)
        }

    private val scheduledExecutorServices =
        ExecutorName.values().associateWith {
            scheduledExecutorProvider(clock)
        }

    override fun backgroundExecutor(executorName: ExecutorName): BlockableExecutorService {
        return checkNotNull(executorServices[executorName])
    }

    override fun scheduledExecutor(executorName: ExecutorName): BlockingScheduledExecutorService {
        return checkNotNull(scheduledExecutorServices[executorName])
    }

    override fun close() {
        executorServices.values.forEach { it.shutdown() }
        scheduledExecutorServices.values.forEach { it.shutdown() }
    }
}
