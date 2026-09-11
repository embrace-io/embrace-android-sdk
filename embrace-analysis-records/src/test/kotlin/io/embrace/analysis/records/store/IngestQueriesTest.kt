package io.embrace.analysis.records.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IngestQueries]' SQL text: the composed-window sentinel and the named-slice window builder. Moved
 * here from `TraceProcessorTest` in `embrace-analysis-perfetto` when the ingest window/signal SQL
 * left the perfetto module for the records module.
 */
class IngestQueriesTest {

    @Test
    fun `windowFor resolves the composed sentinel to the composed window and every other instrument to its named-slice window`() {
        assertEquals(IngestQueries.COMPOSED_WINDOW, IngestQueries.windowFor("composed"))
        assertTrue(IngestQueries.windowFor("emb-sdk-start").contains("s.name = 'emb-sdk-start'"))
        assertTrue(IngestQueries.window("emb-sdk-start").contains("ORDER BY s.ts DESC LIMIT 1"))
    }
}
