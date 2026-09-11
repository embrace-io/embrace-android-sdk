package io.embrace.analysis.reports

import io.embrace.analysis.records.store.IngestQueries
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five bundled SQL resources: each loads a non-empty string, each is read from disk once and
 * memoized (the properties are `by lazy`), and two of them mention the `slice` table they read while
 * the startup one names the span it starts from. The last test documents the other half of the split:
 * the startup report SQL lives here in [StartupQueries], while the ingest window/signal SQL lives in
 * [IngestQueries] in the records module - the perfetto module itself now holds no startup SQL at all.
 */
class StartupQueriesTest {

    @Test
    fun `each SQL property loads a non-empty string and is memoized`() {
        val properties = listOf(
            "STARTUP_METRICS" to { StartupQueries.STARTUP_METRICS },
            "VARIANCE_METRICS" to { StartupQueries.VARIANCE_METRICS },
            "OUTLIER_METRICS" to { StartupQueries.OUTLIER_METRICS },
            "INIT_WINDOW_SCHED" to { StartupQueries.INIT_WINDOW_SCHED },
            "FOREIGN_GC_OVERLAP" to { StartupQueries.FOREIGN_GC_OVERLAP },
        )

        properties.forEach { (name, accessor) ->
            val first = accessor()
            assertTrue(name, first.isNotEmpty())
            assertSame(name, first, accessor())
        }
    }

    @Test
    fun `startup, variance and outlier SQL mention the signals they read`() {
        assertTrue(StartupQueries.STARTUP_METRICS.contains("emb-sdk-start"))
        assertTrue(StartupQueries.VARIANCE_METRICS.contains("slice"))
        assertTrue(StartupQueries.OUTLIER_METRICS.contains("slice"))
    }

    @Test
    fun `the startup report SQL lives in StartupQueries, not in the records module's IngestQueries`() {
        val members = IngestQueries::class.java.declaredFields.map { it.name } +
            IngestQueries::class.java.declaredMethods.map { it.name }
        val movedNames = listOf(
            "STARTUP_METRICS",
            "VARIANCE_METRICS",
            "OUTLIER_METRICS",
            "INIT_WINDOW_SCHED",
            "FOREIGN_GC_OVERLAP",
        )

        movedNames.forEach { moved ->
            assertFalse(moved, members.any { it.contains(moved) })
        }
        assertTrue(members.contains("SIGNALS"))
        assertTrue(members.contains("COMPOSED_WINDOW"))
    }
}
