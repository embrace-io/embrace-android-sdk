package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PrioritizedWorker
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
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
internal class WorkerThreadModuleImpl(
    initModule: InitModule,
) : WorkerThreadModule, RejectedExecutionHandler {

    private val clock = initModule.clock
    private val logger = initModule.logger
    private val executors: MutableMap<Worker, ExecutorService> = ConcurrentHashMap()
    private val prioritizedWorker: MutableMap<Worker, PrioritizedWorker> = ConcurrentHashMap()
    private val backgroundWorker: MutableMap<Worker, BackgroundWorker> = ConcurrentHashMap()
    override val anrMonitorThread: AtomicReference<Thread> = AtomicReference<Thread>()

    override fun prioritizedWorker(worker: Worker): PrioritizedWorker {
        return prioritizedWorker.getOrPut(worker) {
            PrioritizedWorker(fetchExecutor(worker))
        }
    }

    override fun backgroundWorker(worker: Worker): BackgroundWorker {
        return backgroundWorker.getOrPut(worker) {
            BackgroundWorker(fetchExecutor(worker) as ScheduledExecutorService)
        }
    }

    override fun close() {
        executors.values.forEach(ExecutorService::shutdown)
    }

    private fun fetchExecutor(worker: Worker): ExecutorService {
        return executors.getOrPut(worker) {
            val threadFactory = createThreadFactory(worker)

            when (worker) {
                Worker.NetworkRequestWorker, Worker.FileCacheWorker -> PriorityThreadPoolExecutor(
                    clock,
                    threadFactory,
                    this,
                    1,
                    1
                )

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

    private fun createThreadFactory(name: Worker): ThreadFactory {
        return ThreadFactory { runnable: Runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                if (name == Worker.AnrWatchdogWorker) {
                    anrMonitorThread.set(this)
                }
                this.name = "emb-${name.threadName}"
            }
        }
    }
}
