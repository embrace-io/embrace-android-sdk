package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads the committed macrobenchmark capture. Every expected value was measured from that file, so
 * regenerating it means updating them.
 *
 * This is also where unknown fields are exercised: the real trace carries a process tree, ftrace
 * stats, clock snapshots and more, none of which the trimmed schema declares.
 */
internal class PerfettoTraceFixtureTest {

    private val trace = parseTrace(fixture())

    @Test
    fun `every print event in the fixture is paired into a slice`() {
        assertEquals(1094, trace.slices.size)
        assertEquals(0, trace.unmatchedEndCount)
        assertEquals(0, trace.unclosedBeginCount)
        assertEquals(0, trace.ignoredEventCount)
        assertEquals(listOf(9874, 9891, 9892, 9894, 9904, 9905), trace.threadIds)
        assertEquals(946, trace.slices.count { it.tid == 9874 })
    }

    @Test
    fun `sdk sections are separated from app and framework ones`() {
        assertEquals(1089, trace.withPrefix(EMB_PREFIX).size)
        assertEquals(
            setOf(
                "Startup",
                "session-workload",
                "session-end",
                "ProcessLifecycleInitializer",
                "ProfileInstallerInitializer",
            ),
            trace.slices.filterNot { it.name.startsWith(EMB_PREFIX) }.map(TraceSlice::name).toSet(),
        )
    }

    @Test
    fun `sdk startup is a single slice on the thread that started it`() {
        val startup = trace.named("emb-sdk-start").single()

        assertEquals(9874, startup.tid)
        assertEquals(0, startup.depth)
        assertEquals(12_083_458, startup.durationNanos)
    }

    @Test
    fun `sections nest five levels deep, each contained by the slice enclosing it`() {
        assertEquals(listOf(129, 452, 440, 55, 18), (0..4).map { d -> trace.slices.count { it.depth == d } })

        trace.slices.groupBy(TraceSlice::tid).forEach { (tid, slices) ->
            val open = ArrayDeque<TraceSlice>()
            slices.forEach { slice ->
                assertTrue("$slice ends before it starts", slice.endNanos >= slice.startNanos)
                while (open.size > slice.depth) {
                    open.removeLast()
                }
                assertEquals("depth is not contiguous on $tid", slice.depth, open.size)
                open.lastOrNull()?.let { parent ->
                    assertTrue(
                        "$slice escapes $parent",
                        slice.startNanos >= parent.startNanos && slice.endNanos <= parent.endNanos,
                    )
                }
                open.addLast(slice)
            }
        }
    }

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
    }
}
