package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Builds the model from the committed macrobenchmark capture. Every expected value was measured from
 * that file, so regenerating it means updating them.
 */
internal class TraceModelFixtureTest {

    private val model = TraceInterpreter().interpret(ftraceEvents(parseTrace(fixture())))

    @Test
    fun `every atrace event in the capture pairs into a slice`() {
        // 1094 begins and 1094 ends, so a balanced capture leaves nothing over
        assertEquals(1094, model.sliceCount)
        assertEquals(0, model.unclosed)
        assertEquals(0, model.unopened)
        assertEquals(0, model.unsupported)
    }

    @Test
    fun `slices are stacked per thread, keyed and ordered by thread id`() {
        assertEquals(listOf(9874, 9891, 9892, 9894, 9904, 9905), model.threads.keys.toList())
        assertEquals(
            listOf(946, 12, 34, 43, 55, 4),
            model.threads.values.map { it.slices.size },
        )
    }

    @Test
    fun `a threads slices are ordered by start, even though ftrace batches them per cpu`() {
        model.threads.values.forEach { timeline ->
            val starts = timeline.slices.map(TraceSlice::startNanos)
            assertEquals("tid ${timeline.tid}", starts.sorted(), starts)
        }
    }

    @Test
    fun `the sections the sdk emits are searchable by name`() {
        assertEquals(86, model.names.size)

        val start = checkNotNull(model.first("emb-sdk-start"))
        assertEquals(9874, start.tid)
        assertEquals(0, start.depth)
        assertEquals(12_083_458L, start.durationNanos)
        assertEquals(1, model.slices("emb-sdk-start").size)
    }

    @Test
    fun `a repeated section yields every occurrence, ordered by start`() {
        val occurrences = model.slices("emb-mf-span-snapshot-changed")
        assertEquals(501, occurrences.size)
        assertEquals(occurrences.map(TraceSlice::startNanos).sorted(), occurrences.map(TraceSlice::startNanos))
    }

    @Test
    fun `nesting is recovered, so a section knows what enclosed it and what it enclosed`() {
        val start = checkNotNull(model.first("emb-sdk-start"))
        assertNull(start.parent)
        assertEquals(
            listOf("emb-modules-init", "emb-post-init", "emb-post-services-setup", "emb-startup-tracking"),
            start.children.map(TraceSlice::name),
        )
        assertEquals(4, model.threads.values.flatMap { it.slices }.maxOf(TraceSlice::depth))
    }

    @Test
    fun `the recovered tree is the nesting the sdk emits`() {
        // emb-sdk-start > emb-modules-init > emb-span-service-init > emb-otel-tracer-init
        val tracer = checkNotNull(model.first("emb-otel-tracer-init"))
        assertEquals(
            listOf("emb-span-service-init", "emb-modules-init", "emb-sdk-start"),
            generateSequence(tracer.parent, TraceSlice::parent).map(TraceSlice::name).toList(),
        )
        assertEquals(3, tracer.depth)
    }

    @Test
    fun `a parents duration contains its childrens`() {
        model.threads.values.flatMap { it.slices }.forEach { slice ->
            slice.children.forEach { child ->
                assertTrue("$child outside $slice", child.startNanos >= slice.startNanos)
                assertTrue("$child outside $slice", child.endNanos <= slice.endNanos)
            }
        }
    }

    @Test
    fun `a section the capture never recorded is absent rather than an error`() {
        assertEquals(emptyList<TraceSlice>(), model.slices("emb-not-in-this-trace"))
        assertNull(model.first("emb-not-in-this-trace"))
    }

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
    }
}
