package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryRunnableComparator
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.internal.worker.Worker.Priority.DataPersistenceWorker
import io.embrace.android.embracesdk.internal.worker.Worker.Priority.NetworkRequestWorker
import io.embrace.android.embracesdk.internal.worker.comparator.apiRequestComparator
import io.embrace.android.embracesdk.internal.worker.comparator.taskPriorityComparator
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
internal class WorkerThreadModuleImpl(
    initModule: InitModule,
    private val configServiceProvider: Provider<ConfigService>
) : WorkerThreadModule, RejectedExecutionHandler {

    private val logger = initModule.logger
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

            if (worker is Worker.Priority) {
                val comparator = when (worker) {
                    DataPersistenceWorker -> fileCacheWorkercomparator
                    NetworkRequestWorker -> apiRequestComparator
                }
                PriorityThreadPoolExecutor(
                    threadFactory,
                    this,
                    1,
                    1,
                    comparator
                )
            } else {
                ScheduledThreadPoolExecutor(1, threadFactory, this)
            }
        }
    }

    private val fileCacheWorkercomparator by lazy {
        when (configServiceProvider().autoDataCaptureBehavior.isV2StorageEnabled()) {
            true -> storedTelemetryRunnableComparator
            false -> taskPriorityComparator
        }
    }

    /**
     * Handles a rejected task by logging a warning and ignoring the task. Generally speaking
     * the executor will either have been shutdown directly (which should not happen outside of)
     * [WorkerThreadModuleImpl] or the process is terminating.
     *
     * The difference with this policy is that a Future will be returned when the executor is
     * shut down, and the caller must handle any TimeoutExceptions for the Future (that will)
     * never return a value.
     */
    override fun rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor) {
        logger.logWarning(
            "Rejected execution of $runnable on $executor. Ignoring - the process is likely terminating."
        )
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
