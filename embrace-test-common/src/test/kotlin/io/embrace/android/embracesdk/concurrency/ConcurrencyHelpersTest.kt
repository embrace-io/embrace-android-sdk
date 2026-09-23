package io.embrace.android.embracesdk.concurrency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ConcurrencyHelpersTest {

    @Test
    fun `every action runs at the same time on its own thread`() {
        val actionCount = 4
        val allRunning = CountDownLatch(actionCount)

        // each action only finishes once every other action has also started, so this passes only if they overlap
        runConcurrently(actionCount) {
            allRunning.countDown()
            assertTrue(allRunning.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `each action receives its thread index`() {
        val indices = ConcurrentLinkedQueue<Int>()

        runConcurrently(threadCount = 5) { threadIndex ->
            indices.add(threadIndex)
        }

        assertEquals((0 until 5).toSet(), indices.toSet())
        assertEquals(5, indices.size)
    }

    @Test
    fun `a failing action fails the run with its failure as the cause`() {
        val failure = assertThrows(AssertionError::class.java) {
            runActionsConcurrently(
                listOf(
                    {},
                    { throw AssertionError("boom") },
                    {},
                ),
            )
        }

        assertEquals("1 of 3 concurrent actions failed. Race condition? Likely.", failure.message)
        assertEquals("boom", failure.cause?.message)
    }

    @Test
    fun `every failure is reported`() {
        val failure = assertThrows(AssertionError::class.java) {
            runActionsConcurrently(
                listOf(
                    { throw IllegalStateException("first") },
                    { throw IllegalStateException("second") },
                ),
            )
        }

        assertEquals("2 of 2 concurrent actions failed. Race condition? Likely.", failure.message)
        val reported = listOfNotNull(failure.cause?.message) + failure.suppressed.map { it.message }
        assertEquals(setOf("first", "second"), reported.toSet())
    }

    @Test
    fun `actions that do not finish in time fail the run`() {
        val release = CountDownLatch(1)
        try {
            val failure = assertThrows(AssertionError::class.java) {
                runActionsConcurrently(
                    actions = listOf(
                        {},
                        { release.await() },
                    ),
                    timeoutMs = 100,
                )
            }

            assertEquals("1 of 2 concurrent actions did not finish within 100 ms", failure.message)
        } finally {
            release.countDown()
        }
    }
}
