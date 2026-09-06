package io.embrace.startup.store

import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

/**
 * `Ingest.measure` reads every trace independently, so it reads them in parallel. The signal inventory is
 * the one part that is NOT per-trace: it belongs to the run and is taken from its first clean trace. That
 * makes it what a parallel rewrite can quietly get wrong, because "first" would otherwise mean "whichever
 * worker finished first", and the stored record would differ between two identical runs.
 */
class IngestMeasureTest {

    @Test
    fun `signals come from the first trace with a window, whatever order the workers finish in`() {
        // The first eligible trace is also the slowest to read, so an inventory claimed inside the loop
        // would land on a later trace instead.
        val tp = FakeProcessor(windowless = setOf("t1.perfetto-trace"), slowest = "t2.perfetto-trace")
        val traces = (1..5).map { Path.of("t$it.perfetto-trace") }

        val measured = Ingest.measure(tp, traces, Queries.COMPOSED_INSTRUMENT)

        assertEquals("results keep the input's order", traces, measured.map { it.trace })
        assertNull("the trace with no window contributes none", measured[0].windowMs)
        assertEquals(42.5, measured[1].windowMs!!, 0.0)
        assertEquals(
            "only the first trace with a window carries the inventory",
            listOf(null, listOf(SIGNAL), null, null, null),
            measured.map { it.signals },
        )
    }

    @Test
    fun `a run where no trace yields a window stores no signal inventory at all`() {
        val traces = (1..3).map { Path.of("t$it.perfetto-trace") }
        val tp = FakeProcessor(windowless = traces.map { it.fileName.toString() }.toSet())

        val measured = Ingest.measure(tp, traces, Queries.COMPOSED_INSTRUMENT)

        assertEquals(listOf(null, null, null), measured.map { it.signals })
        assertEquals(listOf(null, null, null), measured.map { it.windowMs })
    }

    /**
     * Answers the three ingest queries from canned output, with no binary and no trace on disk. Health and
     * window are told apart by call order, which `measure` fixes: health first, then the window.
     */
    private class FakeProcessor(
        private val windowless: Set<String> = emptySet(),
        private val slowest: String? = null,
    ) : TraceProcessor(Path.of("/nonexistent")) {

        override fun <T> withWarmTrace(trace: Path, block: (QueryTarget) -> T): T {
            if (trace.fileName.toString() == slowest) Thread.sleep(SLOW_MS)
            return block(Target(trace, trace.fileName.toString() in windowless))
        }

        private class Target(override val trace: Path, private val windowless: Boolean) : QueryTarget {
            private var calls = 0

            override fun queryRaw(sql: String): Output {
                if (sql == Queries.SIGNALS) return Output(0, "$SIGNAL\n", "")
                calls++
                // A present canary and no loss counters is a healthy trace.
                if (calls == 1) return Output(0, "canary,1\n", "")
                return Output(0, if (windowless) "" else "42.5\n", "")
            }
        }

        private companion object {
            const val SLOW_MS = 150L
        }
    }

    private companion object {
        const val SIGNAL = "emb-modules-init"
    }
}
