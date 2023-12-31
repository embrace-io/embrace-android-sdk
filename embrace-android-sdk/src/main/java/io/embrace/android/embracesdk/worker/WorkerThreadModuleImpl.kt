package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

// This lint error seems spurious as it only flags methods annotated with @JvmStatic even though the accessor is generated regardless
// for lazily initialized members
internal class WorkerThreadModuleImpl(
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : WorkerThreadModule, RejectedExecutionHandler {

    private val executors: MutableMap<ExecutorName, ExecutorService> = ConcurrentHashMap()

    override fun backgroundExecutor(executorName: ExecutorName): ExecutorService =
        fetchExecutor(executorName)

    override fun scheduledExecutor(executorName: ExecutorName): ScheduledExecutorService {
        if (executorName == ExecutorName.NETWORK_REQUEST) {
            error("Network request executor is not a scheduled executor")
        }
        return fetchExecutor(executorName) as ScheduledExecutorService
    }

    override fun close() {
        executors.values.forEach(ExecutorService::shutdown)
    }

    private fun fetchExecutor(executorName: ExecutorName): ExecutorService {
        return executors.getOrPut(executorName) {
            val threadFactory = createThreadFactory(executorName.threadName)

            when (executorName) {
                ExecutorName.NETWORK_REQUEST -> {
                    ThreadPoolExecutor(
                        1,
                        1,
                        60L,
                        TimeUnit.SECONDS,
                        createNetworkRequestQueue(),
                        threadFactory,
                        this
                    )
                }
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
        logger.logWarning("Rejected execution of $runnable on $executor. Ignoring - the process is likely terminating.")
    }

    private fun createThreadFactory(name: String): ThreadFactory =
        ThreadFactory { runnable: Runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                this.name = "emb-$name"
            }
        }

    private fun createNetworkRequestQueue(): PriorityBlockingQueue<Runnable> {
        return PriorityBlockingQueue(
            100,
            compareBy { runnable: Runnable ->
                when (runnable) {
                    is NetworkRequestRunnable -> -1
                    else -> 0
                }
            }
        )
    }
}
