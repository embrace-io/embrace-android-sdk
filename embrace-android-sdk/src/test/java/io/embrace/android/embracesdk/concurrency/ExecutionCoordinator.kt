package io.embrace.android.embracesdk.concurrency

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.internal.utils.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A class that coordinates the parallel execution of two threads to ensure a deterministic execution order
 * This enables the writing of non-flaky, non-sleep()-based tests to check for race conditions
 */
internal class ExecutionCoordinator(
    private val executionModifiers: ExecutionModifiers,
    private val errorLogsProvider: Provider<List<FakeLoggerAction.LogMessage>>?
) {
    private val thread1 = SingleThreadTestScheduledExecutor()
    private val thread2 = SingleThreadTestScheduledExecutor()
    private val unblockingThread = SingleThreadTestScheduledExecutor()

    /**
     * Execute both operations in separate threads in a coordinated manner
     */
    fun executeOperations(
        first: () -> Unit,
        second: () -> Unit,
        firstBlocksSecond: Boolean,
        firstOperationFails: Boolean = false
    ) {
        var blockId: Int? = null
        var firstOperationUnblocked: Boolean? = null
        val completionLatch = CountDownLatch(2)
        val queueSecondOperationLatch = CountDownLatch(1)
        val unblockFirstOperationLatch = CountDownLatch(1)
        val runOrder = LinkedBlockingDeque<Int>()

        thread1.submit {
            runOrder.offer(1)
            if (firstOperationFails) {
                executionModifiers.errorOnNextOperation()
            } else {
                blockId = executionModifiers.blockNextOperation()
            }
            queueSecondOperationLatch.countDown()
            first()
            runOrder.add(5)
            completionLatch.countDown()
        }

        queueSecondOperationLatch.await(1, TimeUnit.SECONDS)

        thread2.submit {
            runOrder.offer(2)
            unblockingThread.submit {
                unblockFirstOperationLatch.await(1, TimeUnit.SECONDS)
                runOrder.add(4)
                firstOperationUnblocked = executionModifiers.unblockOperation(checkNotNull(blockId))
            }
            runOrder.add(3)

            if (firstBlocksSecond) {
                unblockFirstOperationLatch.countDown()
            }

            second()
            runOrder.add(6)

            if (!firstBlocksSecond) {
                unblockFirstOperationLatch.countDown()
            }
            completionLatch.countDown()
        }

        completionLatch.await(5, TimeUnit.SECONDS)

        assertNull("First task threw exception", thread1.lastThrowable())
        assertNull("Second task threw exception", thread2.lastThrowable())

        if (firstBlocksSecond) {
            assertEquals("Unexpected run order", listOf(1, 2, 3, 4), runOrder.toList().take(4))
        } else {
            assertEquals("Unexpected run order", listOf(1, 2, 3, 6), runOrder.toList().take(4))
        }

        if (!firstOperationFails) {
            assertTrue(checkNotNull(firstOperationUnblocked))
        } else if (errorLogsProvider != null) {
            val errorLogs = errorLogsProvider.invoke()
            assertEquals("The following errors were logged: $errorLogs", 1, errorLogs.size)
        }

        assertLatchFullyCountedDown(completionLatch)
        assertLatchFullyCountedDown(queueSecondOperationLatch)
        assertLatchFullyCountedDown(unblockFirstOperationLatch)
        assertEquals(firstBlocksSecond, runOrder.last == 6)
    }

    /**
     * Kill the first thread. Most useful when the thread is blocked so unexpected mid-execution errors can be simulated.
     */
    fun shutdownFirstThread(): List<Runnable> = thread1.shutdownNow()

    /**
     * Return an error message used for debugging and assertions based on error logs recorded during the execution.
     */
    fun getErrorMessage(): String {
        return if (errorLogsProvider != null) {
            "The following errors were logged: ${errorLogsProvider.invoke()}"
        } else {
            "Error"
        }
    }

    private fun assertLatchFullyCountedDown(latch: CountDownLatch?) {
        checkNotNull(latch)
        assertEquals(0, latch.count)
    }

    /**
     * Provides a standard implementation for [ExecutionModifiers] that [ExecutionCoordinator] expects and validates against.
     * Use [wrapOperation] to wrap code blocks or functions whose execution will by modified by [ExecutionModifiers].
     */
    internal class OperationWrapper : ExecutionModifiers {
        private val operationLatches = ConcurrentHashMap<Int, CountDownLatch>()
        private val operationBlockCounter = AtomicInteger(0)
        private val operationBlocks = LinkedBlockingDeque<Int>()
        override fun blockNextOperation(blockBefore: Boolean): Int {
            val id = operationBlockCounter.incrementAndGet() * if (blockBefore) -1 else 1
            operationLatches[id] = CountDownLatch(1)
            operationBlocks.add(id)
            return id
        }

        override fun unblockOperation(id: Int): Boolean =
            operationLatches[id]?.let { latch ->
                latch.countDown()
                latch.count == 0L
            } ?: false

        override fun errorOnNextOperation() {
            operationBlocks.add(0)
        }

        /**
         * Wrap the enclosed operation so execution will by modified by the [ExecutionModifiers] implementation of this class
         */
        fun <T> wrapOperation(operation: () -> T): T {
            val id = operationBlocks.poll()
            val latch = id?.let { operationLatches[it] }

            if (latch != null && id < 0) {
                latch.await(1, TimeUnit.SECONDS)
            }

            val obj: T = operation()

            if (id == 0) {
                throw IllegalAccessException("Operation failed. Do something!")
            }

            if (latch != null && id > 0) {
                latch.await(1, TimeUnit.SECONDS)
            }

            return obj
        }
    }

    /**
     * Modifications to execution that can be done to test the functionality of the implementing component.
     *
     * This does not typically need to be implemented as [OperationWrapper] should be sufficient for most use cases. If new
     * implementations are needed, they must abide by the expectations of [ExecutionCoordinator].
     */
    internal interface ExecutionModifiers {

        /**
         * Block on the operation of the next components. [blockBefore] determines if the blocking is done before or after the operation
         * has completed. The return value will be unique ID that can be used in conjunction with [unblockOperation] to unblock the
         * operation. A positive number for the ID indicates that the blocking will happen before, while a negative number means
         * blocking will happen after.
         */
        fun blockNextOperation(blockBefore: Boolean = true): Int

        /**
         * Unblock the operation associated with [id]
         */
        fun unblockOperation(id: Int): Boolean

        /**
         * Use to sure that next operation will throw an [IllegalAccessException]
         */
        fun errorOnNextOperation()
    }
}
