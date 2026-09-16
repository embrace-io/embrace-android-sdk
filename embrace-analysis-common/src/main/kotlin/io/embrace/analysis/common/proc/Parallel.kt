package io.embrace.analysis.common.proc

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Bounded parallelism for the per-trace work, which is the tool's whole cost model: a campaign is 200 to
 * 2000 traces, each analysed independently in its own `trace_processor_shell` process, and doing them one
 * at a time leaves a multi-core machine idle for hours.
 *
 * The bound is deliberately low rather than "one per core". Every worker holds a whole parsed trace in
 * the child process's memory, so the ceiling here is RAM, not CPU; and this fleet has a logged incident
 * where concurrent device and Gradle work contended badly enough to corrupt a campaign. Four workers is
 * the default, `STARTUP_TOOLS_JOBS` overrides it, and 1 restores the old strictly-sequential behaviour.
 *
 * Only ever wrap work that is independent per item and writes no shared state: the trace queries qualify
 * because each spawns its own child and its own uniquely named temp files.
 */
object Parallel {

    fun defaultJobs(): Int {
        val configured = System.getenv(JOBS_ENV)?.trim()?.toIntOrNull()
        if (configured != null && configured > 0) return configured
        return minOf(DEFAULT_MAX_JOBS, maxOf(1, Runtime.getRuntime().availableProcessors()))
    }

    /**
     * [transform] over [items], at most [jobs] at a time, results in the input's order. The first failure
     * is rethrown with its own stack trace, not wrapped, and the remaining workers are interrupted.
     */
    fun <T, R> map(items: List<T>, jobs: Int = defaultJobs(), transform: (T) -> R): List<R> {
        if (jobs <= 1 || items.size <= 1) return items.map(transform)
        val pool = Executors.newFixedThreadPool(minOf(jobs, items.size))
        try {
            val futures = items.map { item -> pool.submit(Callable { transform(item) }) }
            return futures.map { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private const val JOBS_ENV = "STARTUP_TOOLS_JOBS"
    private const val DEFAULT_MAX_JOBS = 4
}
