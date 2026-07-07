package io.embrace.android.embracesdk.internal.utils.concurrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class LimitCounterTest {

    @Test
    fun `zero is a valid capacity`() {
        val zeroLimit = LimitCounter(0)
        assertEquals(0, zeroLimit.capacity)
        assertEquals(0, zeroLimit.count)

        assertFalse(zeroLimit.increment())
        assertEquals(0, zeroLimit.count)
    }

    @Test
    fun `Int MAX_VALUE is a valid capacity`() {
        val maxLimit = LimitCounter(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, maxLimit.capacity)
        assertEquals(Int.MAX_VALUE, maxLimit.remaining)
        assertEquals(0, maxLimit.count)
    }

    @Test
    fun `increment succeeds until capacity is reached then fails`() {
        val limit = LimitCounter(3)

        assertTrue(limit.increment())
        assertEquals(1, limit.count)
        assertEquals(2, limit.remaining)

        assertTrue(limit.increment())
        assertTrue(limit.increment())
        assertEquals(3, limit.count)
        assertEquals(0, limit.remaining)

        assertFalse(limit.increment())
        assertEquals(3, limit.count)
        assertFalse(limit.increment())
        assertEquals(3, limit.count)
    }

    @Test
    fun `capacity of one allows exactly one increment`() {
        val limit = LimitCounter(1)

        assertTrue(limit.increment())
        assertFalse(limit.increment())
        assertEquals(1, limit.count)
    }

    @Test
    fun `snapshot reflects capacity and count at time of call`() {
        val limit = LimitCounter(5)
        limit.increment()
        limit.increment()

        val snapshot = limit.snapshot()
        assertEquals(5, snapshot.capacity)
        assertEquals(2, snapshot.count)

        // later increments don't mutate a previously taken snapshot
        limit.increment()
        assertEquals(2, snapshot.count)
    }

    @Test
    fun `reset zeroes the count but preserves capacity`() {
        val limit = LimitCounter(2)
        limit.increment()
        limit.increment()
        assertFalse(limit.increment())

        limit.reset()

        assertEquals(2, limit.capacity)
        assertEquals(0, limit.count)
        assertEquals(2, limit.remaining)

        assertTrue(limit.increment())
        assertEquals(1, limit.count)
    }

    @Test
    fun `reset can be called repeatedly and on an already-empty counter`() {
        val limit = LimitCounter(4)

        limit.reset()
        assertEquals(0, limit.count)

        limit.increment()
        limit.reset()
        limit.reset()
        assertEquals(0, limit.count)
        assertEquals(4, limit.remaining)
    }

    @Test
    fun `concurrent increments never exceed capacity and count matches successes`() {
        val threadCount = 32
        val incrementsPerThread = 1_000
        val capacity = 10_000
        val limit = LimitCounter(capacity)

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val successCount = AtomicInteger(0)

        val futures = (0 until threadCount).map {
            executor.submit {
                startLatch.await()
                repeat(incrementsPerThread) {
                    if (limit.increment()) {
                        successCount.incrementAndGet()
                    }
                }
            }
        }

        startLatch.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        val totalAttempts = threadCount * incrementsPerThread
        val expectedSuccesses = minOf(totalAttempts, capacity)

        assertEquals(expectedSuccesses, successCount.get())
        assertEquals(expectedSuccesses, limit.count)
        assertEquals(capacity, limit.capacity)
        assertFalse(limit.increment())
    }
}
