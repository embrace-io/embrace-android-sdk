package io.embrace.android.embracesdk.worker

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

// This lint error seems spurious as it only flags methods annotated with @JvmStatic even though the accessor is generated regardless
// for lazily initialized members
internal class WorkerThreadModuleImpl : WorkerThreadModule {

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
                ExecutorName.NETWORK_REQUEST -> ThreadPoolExecutor(
                    1,
                    1,
                    60L,
                    TimeUnit.SECONDS,
                    createNetworkRequestQueue(),
                    threadFactory
                )
                else -> Executors.newSingleThreadScheduledExecutor(threadFactory)
            }
        }
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
