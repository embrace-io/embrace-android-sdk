package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.model.TraceCounterSample
import io.embrace.android.embracesdk.internal.perfetto.model.TraceModel
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CounterCalculatorTest {

    @Test
    fun `a counter that only climbs counted what it last read`() {
        val stats = only(samples("bytes", 1024, 3072, 8192))
        assertEquals(3, stats.sampleCount)
        assertEquals(1024L, stats.firstValue)
        assertEquals(8192L, stats.lastValue)
        assertEquals(8192L, stats.maxValue)
        assertEquals(8192L, stats.total)
    }

    @Test
    fun `a counter that restarted counted both runs, since its owner was rebuilt rather than reset`() {
        val stats = only(samples("files", 1, 2, 3, 1, 2))
        assertEquals(3L, stats.maxValue)
        assertEquals(2L, stats.lastValue)
        assertEquals(5L, stats.total)
    }

    @Test
    fun `a counter sampled once counted that value, with nothing before it to measure against`() {
        val stats = only(samples("bytes", 38_632))
        assertEquals(1, stats.sampleCount)
        assertEquals(listOf(38_632L, 38_632L, 38_632L), listOf(stats.firstValue, stats.lastValue, stats.total))
    }

    @Test
    fun `two threads publishing one counter share its tally, rather than splitting it in two`() {
        val stats = only(
            listOf(
                TraceCounterSample("bytes", TID, 1000, 512),
                TraceCounterSample("bytes", OTHER_TID, 1100, 1024),
                TraceCounterSample("bytes", TID, 1200, 2048),
            ),
        )
        assertEquals(listOf(TID, OTHER_TID), stats.tids)
        assertEquals(2048L, stats.total)
    }

    @Test
    fun `a reading is offset from the start of the capture, not the boot clock the trace recorded`() {
        val stats = only(samples("bytes", 1024, 2048), traceStartNanos = 1000)
        assertEquals(listOf(0L, 100L), stats.readings.map(CounterReading::offsetNanos))
        assertEquals(listOf(1024L, 2048L), stats.readings.map(CounterReading::value))
    }

    @Test
    fun `counters report in name order, so two runs of the same capture lay out the same way`() {
        val model = model(samples("zeta", 1) + samples("alpha", 2))
        assertEquals(listOf("alpha", "zeta"), calculateCounters(model, 0).map(CounterStats::name))
    }

    @Test
    fun `a capture with no counters reports none rather than an empty placeholder`() {
        assertEquals(emptyList<CounterStats>(), calculateCounters(model(emptyList()), 0))
    }

    private fun only(samples: List<TraceCounterSample>, traceStartNanos: Long = 0L) =
        calculateCounters(model(samples), traceStartNanos).single()

    private fun model(samples: List<TraceCounterSample>) =
        TraceModel(emptyMap(), samples, 0, 0, 0)

    private fun samples(name: String, vararg values: Long): List<TraceCounterSample> =
        values.mapIndexed { index, value ->
            TraceCounterSample(name, TID, FIRST_TIMESTAMP + index * 100, value)
        }

    private companion object {
        const val TID = 6951
        const val OTHER_TID = 6952
        const val FIRST_TIMESTAMP = 1000L
    }
}
