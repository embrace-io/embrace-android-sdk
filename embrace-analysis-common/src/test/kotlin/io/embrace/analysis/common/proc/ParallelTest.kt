package io.embrace.analysis.common.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two properties [Parallel.map] exists to guarantee over a plain `ExecutorService`: the output
 * keeps the caller's order regardless of finishing order, and a worker's exception surfaces as itself
 * rather than wrapped in [java.util.concurrent.ExecutionException]. [Parallel.defaultJobs] is checked
 * against its own stated bounds rather than a fixed number, since it depends on the machine it runs on.
 */
class ParallelTest {

    @Test
    fun `map preserves input order even when the first item finishes last`() {
        val result = Parallel.map(listOf(0, 1, 2, 3), jobs = 4) { i ->
            if (i == 0) {
                Thread.sleep(SLOW_FIRST_ITEM_MS)
            }
            i * 10
        }

        assertEquals(listOf(0, 10, 20, 30), result)
    }

    @Test
    fun `a worker exception propagates as its own type, not wrapped in ExecutionException`() {
        val error = runCatching {
            Parallel.map(listOf(1, 2, 3), jobs = 4) { i ->
                if (i == 2) {
                    throw BoomException("boom at $i")
                }
                i
            }
        }.exceptionOrNull()

        assertTrue("expected a BoomException, got $error", error is BoomException)
    }

    @Test
    fun `defaultJobs is at least one and never more than the available processors`() {
        val jobs = Parallel.defaultJobs()

        assertTrue("jobs=$jobs should be >= 1", jobs >= 1)
        assertTrue(
            "jobs=$jobs should not exceed ${Runtime.getRuntime().availableProcessors()} processors",
            jobs <= Runtime.getRuntime().availableProcessors(),
        )
    }

    private class BoomException(message: String) : RuntimeException(message)

    private companion object {
        const val SLOW_FIRST_ITEM_MS = 200L
    }
}
