package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.delivery.storedTelemetryRunnableComparator
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference

// This lint error seems spurious as it only flags methods annotated with @JvmStatic even though the accessor is generated regardless
// for lazily initialized members
internal class WorkerThreadModuleImpl : WorkerThreadModule, RejectedExecutionHandler {

    private val executors: MutableMap<Worker, ExecutorService> = ConcurrentHashMap()
    private val priorityWorkers: MutableMap<Worker, PriorityWorker<*>> = ConcurrentHashMap()
    private val backgroundWorkers: MutableMap<Worker, BackgroundWorker> = ConcurrentHashMap()
    override val anrMonitorThread: AtomicReference<Thread> = AtomicReference<Thread>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> {
        return priorityWorkers.getOrPut(worker) {
            PriorityWorker<T>(fetchExecutor(worker))
        } as PriorityWorker<T>
    }

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker {
        return backgroundWorkers.getOrPut(worker) {
            BackgroundWorker(fetchExecutor(worker) as ScheduledExecutorService)
        }
    }

    override fun close() {
        executors.values.forEach(ExecutorService::shutdown)
    }

    private fun fetchExecutor(worker: Worker): ExecutorService {
        return executors.getOrPut(worker) {
            val threadFactory = createThreadFactory(worker)

            if (worker is Worker.Priority.DataPersistenceWorker) {
                PriorityThreadPoolExecutor(
                    threadFactory,
                    this,
                    1,
                    1,
                    storedTelemetryRunnableComparator
                )
            } else {
                ScheduledThreadPoolExecutor(1, threadFactory, this)
            }
        }
    }

    /**
     * Handles a rejected task ignoring the task. Generally speaking
     * the executor will either have been shutdown directly (which should not happen outside of)
     * [WorkerThreadModuleImpl] or the process is terminating.
     *
     * The difference with this policy is that a Future will be returned when the executor is
     * shut down, and the caller must handle any TimeoutExceptions for the Future (that will)
     * never return a value.
     */
    override fun rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor) {
    }

    private fun createThreadFactory(name: Worker): ThreadFactory {
        return ThreadFactory { runnable: Runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                if (name == Worker.Background.AnrWatchdogWorker) {
                    anrMonitorThread.set(this)
                }
                this.name = "emb-${name.threadName}"
            }
        }
    }
}
