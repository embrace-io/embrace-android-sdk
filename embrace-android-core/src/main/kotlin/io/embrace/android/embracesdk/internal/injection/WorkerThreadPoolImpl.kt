package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.delivery.storedTelemetryRunnableComparator
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.RunnableScheduledFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// This lint error seems spurious as it only flags methods annotated with @JvmStatic even though the accessor is generated regardless
// for lazily initialized members
class WorkerThreadPoolImpl(
    private val errorHandler: ErrorHandler,
    private val watchdogIdleTimeoutMs: Long = WATCHDOG_IDLE_TIMEOUT_MS,
) : WorkerThreadPool, RejectedExecutionHandler {

    private val executors: MutableMap<Worker, ExecutorService> = ConcurrentHashMap()
    private val priorityWorkers: MutableMap<Worker, PriorityWorker<*>> = ConcurrentHashMap()
    private val backgroundWorkers: MutableMap<Worker, BackgroundWorker> = ConcurrentHashMap()
    override val threadBlockageMonitorThread: AtomicReference<Thread> = AtomicReference<Thread>()

    @Volatile
    private var closed = false

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
        closed = true
        executors.values.forEach { runCatching { shutdown(it) } }
    }

    /**
     * Shuts down the executor, letting tasks that are already due run but dropping timers that are not yet due.
     * Periodic tasks are dropped by [ScheduledThreadPoolExecutor]'s default shutdown policy.
     *
     * Delayed tasks must be cancelled here rather than via `executeExistingDelayedTasksAfterShutdownPolicy = false`,
     * because on some versions of Android the flag also cancels queued tasks with no delay. This
     * could drop cleanup  work that is submitted during teardown.
     */
    private fun shutdown(executor: ExecutorService) {
        runCatching {
            executor.shutdown()
            (executor as? ScheduledThreadPoolExecutor)?.queue?.toList()?.forEach { task ->
                if (task is RunnableScheduledFuture<*> && !task.isPeriodic && task.getDelay(TimeUnit.NANOSECONDS) > 0) {
                    task.cancel(false)
                }
            }
        }
    }

    override fun awaitTermination(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        return executors.values.all { executor ->
            executor.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        }
    }

    private fun fetchExecutor(worker: Worker): ExecutorService {
        val executor = createOrGetExecutor(worker)
        if (closed) {
            shutdown(executor)
        }
        return executor
    }

    private fun createOrGetExecutor(worker: Worker): ExecutorService {
        return executors.getOrPut(worker) {
            val threadFactory = createThreadFactory(worker)

            if (worker is Worker.Priority.DataPersistenceWorker) {
                object : PriorityThreadPoolExecutor(
                    threadFactory,
                    this,
                    1,
                    1,
                    storedTelemetryRunnableComparator,
                ) {
                    override fun afterExecute(r: Runnable, t: Throwable?) = trackTaskFailure(r, t)
                }
            } else {
                object : ScheduledThreadPoolExecutor(1, threadFactory, this) {
                    override fun afterExecute(r: Runnable, t: Throwable?) = trackTaskFailure(r, t)
                }.apply {
                    removeOnCancelPolicy = true

                    // thread is unused in background, allow cleanup
                    if (worker == Worker.Background.ThreadBlockageWatchdogWorker) {
                        setKeepAliveTime(watchdogIdleTimeoutMs, TimeUnit.MILLISECONDS)
                        allowCoreThreadTimeOut(true)
                    }
                }
            }
        }
    }

    /**
     * Handles a rejected task by cancelling it instead of throwing. Generally speaking
     * the executor will either have been shutdown directly (which should not happen outside of)
     * [WorkerThreadPoolImpl] or the process is terminating.
     *
     * The submitter still gets a Future, which is already cancelled, so anyone waiting on it fails
     * fast with a CancellationException rather than blocking until a timeout.
     */
    override fun rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor) {
        runCatching {
            (runnable as? Future<*>)?.cancel(false)
        }
    }

    internal fun trackTaskFailure(runnable: Runnable, throwable: Throwable?) {
        val failure = throwable ?: (runnable as? Future<*>)?.takeIf { it.isDone && !it.isCancelled }?.let {
            try {
                it.get()
                null
            } catch (exc: ExecutionException) {
                exc.cause
            }
        }
        failure?.let { runCatching { errorHandler.trackSdkError(SdkErrorType.WorkerTaskFail, it) } }
    }

    private fun createThreadFactory(name: Worker): ThreadFactory {
        return ThreadFactory { runnable: Runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                if (name == Worker.Background.ThreadBlockageWatchdogWorker) {
                    threadBlockageMonitorThread.set(this)
                }
                this.name = "emb-${name.threadName}"
            }
        }
    }

    private companion object {
        const val WATCHDOG_IDLE_TIMEOUT_MS = 60_000L
    }
}
