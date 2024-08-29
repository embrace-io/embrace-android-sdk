package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.embrace.android.embracesdk.internal.worker.WorkerName
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
) : WorkerThreadModule, RejectedExecutionHandler {

    private val clock = initModule.clock
    private val logger = initModule.logger
    private val executors: MutableMap<WorkerName, ExecutorService> = ConcurrentHashMap()
    private val backgroundWorkers: MutableMap<WorkerName, BackgroundWorker> = ConcurrentHashMap()
    private val scheduledWorkers: MutableMap<WorkerName, ScheduledWorker> = ConcurrentHashMap()
    override val anrMonitorThread: AtomicReference<Thread> = AtomicReference<Thread>()

    override fun backgroundWorker(workerName: WorkerName): BackgroundWorker {
        return backgroundWorkers.getOrPut(workerName) {
            BackgroundWorker(fetchExecutor(workerName))
        }
    }

    override fun scheduledWorker(workerName: WorkerName): ScheduledWorker {
        if (workerName == WorkerName.NETWORK_REQUEST) {
            error("Network request executor is not a scheduled executor")
        }
        return scheduledWorkers.getOrPut(workerName) {
            ScheduledWorker(fetchExecutor(workerName) as ScheduledExecutorService)
        }
    }

    override fun close() {
        executors.values.forEach(ExecutorService::shutdown)
    }

    private fun fetchExecutor(workerName: WorkerName): ExecutorService {
        return executors.getOrPut(workerName) {
            val threadFactory = createThreadFactory(workerName)

            when (workerName) {
                WorkerName.NETWORK_REQUEST -> PriorityThreadPoolExecutor(clock, threadFactory, this, 1, 1)
                else -> ScheduledThreadPoolExecutor(1, threadFactory, this)
            }
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

    private fun createThreadFactory(name: WorkerName): ThreadFactory {
        return ThreadFactory { runnable: Runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                if (name == WorkerName.ANR_MONITOR) {
                    anrMonitorThread.set(this)
                }
                this.name = "emb-${name.threadName}"
            }
        }
    }
}
