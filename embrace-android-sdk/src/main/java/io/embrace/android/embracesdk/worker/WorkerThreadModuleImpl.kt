package io.embrace.android.embracesdk.worker

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

// This lint error seems spurious as it only flags methods annotated with @JvmStatic even though the accessor is generated regardless
// for lazily initialized members
internal class WorkerThreadModuleImpl : WorkerThreadModule {

    private val executors: MutableMap<ExecutorName, ScheduledExecutorService> = ConcurrentHashMap()

    private fun fetchExecutor(executorName: ExecutorName): ScheduledExecutorService {
        return executors.getOrPut(executorName) {
            Executors.newSingleThreadScheduledExecutor(createThreadFactory(executorName.threadName))
        }
    }

    private fun createThreadFactory(name: String): ThreadFactory = ThreadFactory { runnable: Runnable ->
        Executors.defaultThreadFactory().newThread(runnable).apply {
            this.name = "emb-$name"
        }
    }

    override fun backgroundExecutor(executorName: ExecutorName): ExecutorService =
        fetchExecutor(executorName)

    override fun scheduledExecutor(executorName: ExecutorName): ScheduledExecutorService =
        fetchExecutor(executorName)

    override fun close() {
        executors.values.forEach(ScheduledExecutorService::shutdown)
    }
}
