package io.embrace.android.embracesdk.concurrency

import org.jetbrains.annotations.TestOnly
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * An [ExecutorService] that can be set to a mode that blocks tasks from executing unless explicitly unblocked. In either mode,
 * when they run, they are executed on the current thread so their execution is predictable in tests.
 *
 * While blocking mode defaults to false, it can be instantiated in in blocking mode using the constructor parameter "blockingMode"
 * (and subsequently toggled using the [blockingMode] attribute). So by default, this executor behaves like
 * [com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService] unless switched to blocking mode.
 */
public class BlockableExecutorService(blockingMode: Boolean = false) : AbstractExecutorService() {
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private var shutdown = false

    @Volatile
    public var blockingMode: Boolean = blockingMode
        set(value) {
            field = value
            if (!field) {
                runCurrentlyBlocked()
            }
        }

    /**
     * Unblock and run all submitted tasks. New submissions will NOT be run until explicitly told to after the current batch of tasks
     * are completed.
     */
    public fun runCurrentlyBlocked() {
        rejectIfShutdown()
        var taskCount = tasks.size
        if (taskCount > 0) {
            var task = tasks.poll()

            while (task != null && taskCount > 0) {
                task.run()
                taskCount--
                if (taskCount > 0) {
                    task = tasks.poll()
                }
            }
        }
    }

    /**
     * Unblock and run one (1) submitted task at the head of the queue if it exists
     */
    public fun runNext() {
        rejectIfShutdown()
        tasks.poll()?.run()
    }

    /**
     * Return the number of blocked tasks
     */
    public fun tasksBlockedCount(): Int = tasks.size

    @TestOnly
    override fun execute(command: Runnable?) {
        checkNotNull(command)
        rejectIfShutdown()
        tasks.add(command)
        if (!blockingMode) {
            runCurrentlyBlocked()
        }
    }

    override fun shutdown() {
        do {
            tasks.poll()?.run()
        } while (tasks.isNotEmpty())

        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        shutdown = true
        val remainingTasks = tasks.toMutableList()
        tasks.clear()
        return remainingTasks
    }

    override fun isShutdown(): Boolean {
        return shutdown
    }

    override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = throw UnsupportedOperationException()

    private fun rejectIfShutdown() {
        if (isShutdown) {
            throw RejectedExecutionException()
        }
    }
}
