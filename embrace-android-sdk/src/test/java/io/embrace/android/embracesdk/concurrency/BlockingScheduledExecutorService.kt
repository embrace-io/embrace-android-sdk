package io.embrace.android.embracesdk.concurrency

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.fakes.FakeClock
import java.util.LinkedList
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RunnableScheduledFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * An [ScheduledExecutorService] to be used for tests that will block the execution of tasks until explicitly unblocked. You can simulate
 * moving forward in time to triggered the execution of tasks scheduled for the future, and they will be run in the appropriate execution
 * order on the current thread.
 *
 * Note that worked to be run immediately will be sent directly to the instance of [ScheduledExecutorService] used internally. Work
 * scheduled for the future will be stored in separately until it is time for it to execute, which will be done on the delegate.
 *
 * Limitations:
 *
 * - [awaitTermination] is not implemented and will throw a [NotImplementedError]. Code that requires that method cannot use this.
 * - The value of [ScheduledFuture.get] for [schedule] when you pass in a [Callable] will not return the execution result.
 *
 */
@InternalApi
internal class BlockingScheduledExecutorService(
    private val fakeClock: FakeClock = FakeClock(),
    blockingMode: Boolean = true
) : AbstractExecutorService(), ScheduledExecutorService {
    val scheduledTasks = PriorityBlockingQueue(10, BlockedScheduledFutureTaskComparator())
    private val delegateExecutorService = BlockableExecutorService(blockingMode = blockingMode)

    /**
     * Run all tasks due to run at the current time and return when all the tasks have finished running. This does not include tasks
     * submitted during the running of these tasks.
     */
    fun runCurrentlyBlocked() {
        rejectIfShutdown()
        val tasksToRun = LinkedList<Runnable>()
        var nextTask = scheduledTasks.peek()
        while (nextTask != null) {
            nextTask = if (nextTask.executionTimeMs <= fakeClock.now()) {
                val task = scheduledTasks.poll()
                if (task != null) {
                    tasksToRun.add(task)
                }
                scheduledTasks.peek()
            } else {
                null
            }
        }

        tasksToRun.forEach { submit(it) }
        delegateExecutorService.runCurrentlyBlocked()
    }

    /**
     * Runs all tasks that have been submitted to the ExecutorService, regardless of scheduled time.
     */
    fun runAllSubmittedTasks() {
        rejectIfShutdown()
        val tasksToRun = LinkedList<Runnable>()
        var nextTask = scheduledTasks.peek()
        while (nextTask != null) {
            nextTask = scheduledTasks.poll()
            if (nextTask != null) {
                tasksToRun.add(nextTask)
            }
        }

        tasksToRun.forEach { submit(it) }
        delegateExecutorService.runCurrentlyBlocked()
    }

    /**
     * Move time forward and run the tasks that are expected to be run by that time.
     */
    fun moveForwardAndRunBlocked(timeIncrementMs: Long) {
        rejectIfShutdown()
        fakeClock.tick(timeIncrementMs)
        runCurrentlyBlocked()
    }

    fun scheduledTasksCount(): Int = scheduledTasks.size

    var submitCount = 0

    override fun execute(command: Runnable?) {
        requireNotNull(command)
        delegateExecutorService.execute(command)
    }

    override fun submit(task: Runnable?): Future<*> {
        submitCount++
        requireNotNull(task)
        return delegateExecutorService.submit(task)
    }

    override fun shutdown() {
        drainToDelegate()
        delegateExecutorService.shutdown()
    }

    override fun shutdownNow(): MutableList<Runnable> {
        drainToDelegate()
        return delegateExecutorService.shutdownNow()
    }

    override fun isShutdown(): Boolean = delegateExecutorService.isShutdown

    override fun isTerminated(): Boolean = delegateExecutorService.isTerminated

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = throw UnsupportedOperationException()

    override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<Unit> {
        requireNotNull(command)
        requireNotNull(unit)
        require(delay >= 0) { "The delay parameter cannot be negative" }

        rejectIfShutdown()
        val futureTask = BlockedFutureScheduledTask<Unit>(
            runnable = command,
            executionTimeMs = fakeClock.now() + unit.toMillis(delay)
        )

        submitOrQueue(delay, futureTask)

        return futureTask
    }

    /**
     * This implementation is only partially working as the [ScheduledFuture] doesn't return the result of the [Callable]. This needs to be
     * fixed if need to test the result of the [Callable] run from the [ScheduledFuture]... in the future.
     */
    override fun <V> schedule(callable: Callable<V>?, delay: Long, unit: TimeUnit?): ScheduledFuture<V> {
        requireNotNull(callable)
        requireNotNull(unit)
        require(delay >= 0) { "The delay parameter cannot be negative" }
        rejectIfShutdown()
        val futureTask = BlockedFutureScheduledTask(
            callable = callable,
            executionTimeMs = fakeClock.now() + unit.toMillis(delay)
        )

        submitOrQueue(delay, futureTask)

        return futureTask
    }

    override fun scheduleAtFixedRate(command: Runnable?, initialDelay: Long, period: Long, unit: TimeUnit?): ScheduledFuture<Unit> {
        requireNotNull(command)
        requireNotNull(unit)
        require(initialDelay >= 0) { "The initialDelay parameter cannot be negative" }
        require(period > 0) { "The period parameter has to be positive number" }
        rejectIfShutdown()
        val futureTask = BlockedFutureScheduledTask<Unit>(
            runnable = command,
            executionTimeMs = fakeClock.now() + unit.toMillis(initialDelay),
            periodMs = unit.toMillis(period)
        )

        if (initialDelay <= 0L) {
            submit(futureTask)
        } else {
            scheduledTasks.add(futureTask)
        }

        return futureTask
    }

    override fun scheduleWithFixedDelay(command: Runnable?, initialDelay: Long, delay: Long, unit: TimeUnit?): ScheduledFuture<Unit> {
        requireNotNull(command)
        requireNotNull(unit)
        require(initialDelay >= 0) { "The initialDelay parameter cannot be negative" }
        require(delay > 0) { "The delay parameter has to be positive number" }
        rejectIfShutdown()
        val futureTask = BlockedFutureScheduledTask<Unit>(
            runnable = command,
            executionTimeMs = fakeClock.now() + unit.toMillis(initialDelay),
            periodMs = unit.toMillis(delay),
            runFromLastExecution = true
        )

        if (initialDelay <= 0L) {
            submit(futureTask)
        } else {
            scheduledTasks.add(futureTask)
        }

        return futureTask
    }

    private fun <V> submitOrQueue(delay: Long, futureTask: BlockedFutureScheduledTask<V>) {
        if (delay <= 0L) {
            submit(futureTask)
        } else {
            scheduledTasks.add(futureTask)
        }
    }

    private fun drainToDelegate() {
        do {
            val task = scheduledTasks.poll()
            if (task != null) {
                submit(task)
            }
        } while (task != null)
    }

    private fun rejectIfShutdown() {
        if (isShutdown) {
            throw RejectedExecutionException()
        }
    }

    inner class BlockedFutureScheduledTask<V> : FutureTask<V>, RunnableScheduledFuture<V> {
        var executionTimeMs: Long
        private val periodMs: Long
        private val runFromLastExecution: Boolean

        constructor(
            runnable: Runnable,
            executionTimeMs: Long,
            periodMs: Long = 0L,
            runFromLastExecution: Boolean = false
        ) : super(runnable, null) {
            this.executionTimeMs = executionTimeMs
            this.periodMs = periodMs
            this.runFromLastExecution = runFromLastExecution
        }

        constructor(
            callable: Callable<V>,
            executionTimeMs: Long
        ) : super(callable) {
            this.executionTimeMs = executionTimeMs
            this.periodMs = 0L
            this.runFromLastExecution = false
        }

        override fun run() {
            if (!isPeriodic) {
                super.run()
            } else {
                runAndReset()
                if (runFromLastExecution) {
                    executionTimeMs = fakeClock.now() + periodMs
                } else {
                    executionTimeMs += periodMs
                }
                if (executionTimeMs <= fakeClock.now()) {
                    submit(this)
                    runCurrentlyBlocked()
                } else {
                    scheduledTasks.add(this)
                }
            }
        }

        override fun compareTo(other: Delayed?): Int {
            requireNotNull(other)
            val delay = executionTimeMs - fakeClock.now()
            return delay.compareTo(other.getDelay(TimeUnit.MILLISECONDS))
        }

        override fun getDelay(unit: TimeUnit?): Long {
            requireNotNull(unit)
            return unit.convert(executionTimeMs - fakeClock.now(), TimeUnit.MILLISECONDS)
        }

        override fun isPeriodic(): Boolean = periodMs != 0L

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            val cancelled = super.cancel(mayInterruptIfRunning)
            if (cancelled) {
                scheduledTasks.remove(this)
            }
            return cancelled
        }
    }

    private class BlockedScheduledFutureTaskComparator : Comparator<BlockedFutureScheduledTask<*>> {
        override fun compare(taskA: BlockedFutureScheduledTask<*>, taskB: BlockedFutureScheduledTask<*>): Int {
            return taskA.executionTimeMs.compareTo(taskB.executionTimeMs)
        }
    }
}
