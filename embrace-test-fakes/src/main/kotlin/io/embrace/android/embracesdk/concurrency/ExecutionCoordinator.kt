package io.embrace.android.embracesdk.concurrency

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A class that coordinates the parallel execution of two threads to ensure a deterministic execution order
 * This enables the writing of non-flaky, non-sleep()-based tests to check for race conditions
 */
class ExecutionCoordinator {

    /**
     * Provides a standard implementation for [ExecutionModifiers] that [ExecutionCoordinator] expects and validates against.
     * Use [wrapOperation] to wrap code blocks or functions whose execution will by modified by [ExecutionModifiers].
     */
    class OperationWrapper : ExecutionModifiers {
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
    interface ExecutionModifiers {

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
