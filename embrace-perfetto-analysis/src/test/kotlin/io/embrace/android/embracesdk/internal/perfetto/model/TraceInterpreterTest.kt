package io.embrace.android.embracesdk.internal.perfetto.model

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.PrintFtraceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TraceInterpreterTest {

    @Test
    fun `a begin and its end become one slice, timed by the events that bounded it`() {
        val model = build(begin("outer"), end())
        val slice = checkNotNull(model.first("outer"))
        assertEquals("outer", slice.name)
        assertEquals(TID, slice.tid)
        assertEquals(1000L, slice.startNanos)
        assertEquals(1100L, slice.endNanos)
        assertEquals(100L, slice.durationNanos)
        assertEquals(0, slice.depth)
        assertNull(slice.parent)
        assertEquals(emptyList<TraceSlice>(), slice.children)
    }

    @Test
    fun `nested begins record their depth and are linked to the section enclosing them`() {
        val model = build(begin("outer"), begin("middle"), begin("inner"), end(), end(), end())
        val outer = checkNotNull(model.first("outer"))
        val middle = checkNotNull(model.first("middle"))
        val inner = checkNotNull(model.first("inner"))

        assertEquals(listOf(0, 1, 2), listOf(outer.depth, middle.depth, inner.depth))
        assertEquals(listOf(middle), outer.children)
        assertEquals(listOf(inner), middle.children)
        assertEquals(emptyList<TraceSlice>(), inner.children)
        assertNull(outer.parent)
        assertSame(outer, middle.parent)
        assertSame(middle, inner.parent)
    }

    @Test
    fun `a parents duration spans its children, and siblings stay in start order`() {
        val model = build(begin("outer"), begin("first"), end(), begin("second"), end(), end())
        val outer = checkNotNull(model.first("outer"))
        assertEquals(listOf("first", "second"), outer.children.map(TraceSlice::name))
        assertTrue(outer.children.all { it.startNanos >= outer.startNanos && it.endNanos <= outer.endNanos })
    }

    @Test
    fun `events handed over out of timestamp order still pair, since ftrace batches per cpu`() {
        val model = build(
            event(1300, "E|$TGID"),
            event(1000, "B|$TGID|outer"),
            event(1200, "E|$TGID"),
            event(1100, "B|$TGID|inner"),
        )

        val outer = checkNotNull(model.first("outer"))
        val inner = checkNotNull(model.first("inner"))
        assertEquals(1000L to 1300L, outer.startNanos to outer.endNanos)
        assertEquals(1100L to 1200L, inner.startNanos to inner.endNanos)
        assertSame(outer, inner.parent)
        assertEquals(0, model.unclosed)
        assertEquals(0, model.unopened)
    }

    @Test
    fun `threads are stacked independently, so interleaved sections do not nest across them`() {
        val model = build(
            event(1000, "B|$TGID|main-work", tid = TID),
            event(1050, "B|$TGID|worker-work", tid = OTHER_TID),
            event(1100, "E|$TGID", tid = TID),
            event(1150, "E|$TGID", tid = OTHER_TID),
        )

        assertEquals(setOf(TID, OTHER_TID), model.threads.keys)
        val main = checkNotNull(model.first("main-work"))
        val worker = checkNotNull(model.first("worker-work"))
        assertEquals(TID, main.tid)
        assertEquals(OTHER_TID, worker.tid)
        assertNull(main.parent)
        assertNull(worker.parent)
        assertEquals(listOf(main), model.threads.getValue(TID).slices)
        assertEquals(listOf(worker), model.threads.getValue(OTHER_TID).slices)
    }

    @Test
    fun `threads are ordered by id, so the same trace always summarises the same way`() {
        val model = build(
            event(1000, "B|$TGID|late", tid = OTHER_TID),
            event(1100, "E|$TGID", tid = OTHER_TID),
            event(1200, "B|$TGID|early", tid = TID),
            event(1300, "E|$TGID", tid = TID),
        )
        assertEquals(listOf(TID, OTHER_TID), model.threads.keys.toList())
    }

    @Test
    fun `a section that never closed is counted, and the sections within it are kept`() {
        val model = build(begin("never-closed"), begin("completed"), end())
        assertEquals(1, model.unclosed)
        assertEquals(0, model.unopened)
        assertNull(model.first("never-closed"))

        val completed = checkNotNull(model.first("completed"))
        assertNull(completed.parent)
        assertEquals(1, completed.depth)
        assertEquals(listOf(completed), model.threads.getValue(TID).roots)
    }

    @Test
    fun `an end with no begin is counted and leaves the surrounding sections intact`() {
        val model = build(end(), begin("after"), end())
        assertEquals(1, model.unopened)
        assertEquals(0, model.unclosed)
        val after = checkNotNull(model.first("after"))
        assertEquals(0, after.depth)
        assertEquals(1, model.sliceCount)
    }

    @Test
    fun `payloads that are not synchronous slices are counted without disturbing the nesting`() {
        val model = build(
            begin("outer"),
            event(1100, "S|$TGID|async|7"),
            begin("inner", timestamp = 1200),
            event(1250, "F|$TGID|async|7"),
            end(timestamp = 1300),
            event(1350, "not an atrace payload"),
            end(timestamp = 1400),
        )
        assertEquals(3, model.unsupported)
        assertEquals(0, model.unclosed)
        assertEquals(0, model.unopened)
        assertEquals(2, model.sliceCount)
        assertSame(model.first("outer"), checkNotNull(model.first("inner")).parent)
    }

    @Test
    fun `a counter becomes a sample timed by the event that wrote it, not a slice`() {
        val model = build(begin("outer"), counter("bytes-written", 4096, timestamp = 1100), end(timestamp = 1200))
        assertEquals(TraceCounterSample("bytes-written", TID, 1100, 4096), model.counterSamples("bytes-written").single())
        assertEquals(setOf("bytes-written"), model.counterNames)
        assertEquals(1, model.sliceCount)
        assertEquals(0, model.unsupported)
    }

    @Test
    fun `samples are ordered by when they were written, interleaving the threads that wrote them`() {
        val model = build(
            counter("bytes-written", 300, timestamp = 1300, tid = TID),
            counter("bytes-written", 100, timestamp = 1100, tid = OTHER_TID),
            counter("files-written", 1, timestamp = 1200, tid = TID),
            counter("bytes-written", 200, timestamp = 1200, tid = TID),
        )
        val samples = model.counterSamples("bytes-written")
        assertEquals(listOf(100L, 200L, 300L), samples.map(TraceCounterSample::value))
        assertEquals(listOf(OTHER_TID, TID, TID), samples.map(TraceCounterSample::tid))
        assertEquals(4, model.counterSampleCount)
        assertEquals(emptyList<TraceCounterSample>(), model.counterSamples("absent"))
    }

    @Test
    fun `a counter that does not parse is counted as unsupported rather than sampled`() {
        val model = build(event(1000, "C|$TGID|no-value"))
        assertEquals(1, model.unsupported)
        assertEquals(0, model.counterSampleCount)
    }

    @Test
    fun `every occurrence of a name is searchable, ordered by start, with the earliest first`() {
        val model = build(
            event(1000, "B|$TGID|repeated", tid = TID),
            event(1100, "E|$TGID", tid = TID),
            event(900, "B|$TGID|repeated", tid = OTHER_TID),
            event(950, "E|$TGID", tid = OTHER_TID),
            event(1200, "B|$TGID|repeated", tid = TID),
            event(1400, "E|$TGID", tid = TID),
        )
        val occurrences = model.slices("repeated")
        assertEquals(listOf(900L, 1000L, 1200L), occurrences.map(TraceSlice::startNanos))
        assertEquals(listOf(50L, 100L, 200L), occurrences.map(TraceSlice::durationNanos))
        assertSame(occurrences.first(), model.first("repeated"))
        assertEquals(setOf("repeated"), model.names)
    }

    @Test
    fun `a name the trace never recorded is absent rather than an error`() {
        val model = build(begin("present"), end())
        assertEquals(emptyList<TraceSlice>(), model.slices("absent"))
        assertNull(model.first("absent"))
    }

    @Test
    fun `a threads slices are flattened in start order, parents before the sections within them`() {
        val model = build(begin("outer"), begin("inner"), end(), end())
        assertEquals(listOf("outer", "inner"), model.threads.getValue(TID).slices.map(TraceSlice::name))
    }

    @Test
    fun `events atrace did not write are ignored rather than counted or given a thread`() {
        val model = build(FtraceEvent(timestamp = 1000, pid = OTHER_TID), begin("real"), end())
        assertEquals(setOf(TID), model.threads.keys)
        assertEquals(0, model.unsupported)
        assertEquals(1, model.sliceCount)
    }

    @Test
    fun `a trace with no atrace events yields an empty model rather than failing`() {
        val model = build()
        assertEquals(emptyMap<Int, ThreadTimeline>(), model.threads)
        assertEquals(0, model.sliceCount)
        assertEquals(emptySet<String>(), model.names)
        assertNull(model.first("anything"))
        assertEquals(0, model.counterSampleCount)
        assertEquals(emptySet<String>(), model.counterNames)
    }

    @Test
    fun `a second trace read by one interpreter reports itself, not the sum of both`() {
        val interpreter = TraceInterpreter()
        interpreter.interpret(listOf(begin("unclosed"), end(), end(), counter("bytes-written", 4096)))

        val model = interpreter.interpret(listOf(begin("clean"), end()))
        assertEquals(0, model.unclosed)
        assertEquals(0, model.unopened)
        assertEquals(1, model.sliceCount)
        assertEquals(0, model.counterSampleCount)
        assertEquals(emptyList<TraceCounterSample>(), model.counterSamples("bytes-written"))
    }

    private fun build(vararg events: FtraceEvent) = TraceInterpreter().interpret(events.toList())

    private var nextTimestamp = 1000L

    private fun begin(name: String, timestamp: Long = nextTimestamp()) = event(timestamp, "B|$TGID|$name")

    private fun end(timestamp: Long = nextTimestamp()) = event(timestamp, "E|$TGID")

    private fun counter(name: String, value: Long, timestamp: Long = nextTimestamp(), tid: Int = TID) =
        event(timestamp, "C|$TGID|$name|$value", tid)

    private fun event(timestamp: Long, payload: String, tid: Int = TID) =
        FtraceEvent(timestamp = timestamp, pid = tid, print = PrintFtraceEvent(buf = "$payload\n"))

    private fun nextTimestamp(): Long = nextTimestamp.also { nextTimestamp += 100 }

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9891
        const val TGID = 9874
    }
}
