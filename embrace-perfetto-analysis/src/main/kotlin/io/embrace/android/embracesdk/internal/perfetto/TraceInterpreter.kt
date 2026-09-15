package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent

/**
 * Pairs atrace begin and end events into the slices they describe.
 *
 * Atrace names a section only when it opens, so an end closes whichever begin is innermost on its
 * thread. Two things follow: slices stack per **thread**, using the ftrace event's `pid` rather than
 * the tgid in the payload, and each thread's events are sorted by timestamp first, since ftrace
 * batches per CPU and hands them over out of order.
 *
 * An event that cannot be paired is counted and skipped.
 */
internal class TraceInterpreter {

    private var unclosed = 0
    private var unopened = 0
    private var unsupported = 0

    fun interpret(events: List<FtraceEvent>, threadNames: Map<Int, String> = emptyMap()): TraceModel {
        unclosed = 0
        unopened = 0
        unsupported = 0
        val threads = printEvents(events)
            .groupBy(FtraceEvent::pid)
            .toSortedMap()
            .mapValues { (tid, threadEvents) -> timeline(tid, threadNames[tid], threadEvents) }
        return TraceModel(threads, unclosed, unopened, unsupported)
    }

    private fun timeline(tid: Int, name: String?, events: List<FtraceEvent>): ThreadTimeline {
        val stack = ArrayDeque<OpenSlice>()
        val roots = mutableListOf<TraceSlice>()

        events.sortedBy(FtraceEvent::timestamp).forEach { event ->
            when (val payload = parseAtracePayload(event.print?.buf.orEmpty())) {
                is AtracePayload.Begin -> stack.addLast(OpenSlice(payload.name, event.timestamp, stack.size))
                AtracePayload.End -> close(tid, event.timestamp, stack, roots)
                is AtracePayload.Unsupported -> unsupported++
            }
        }
        drain(stack, roots)
        return ThreadTimeline(tid, name, roots.flatMap(::flatten).sortedBy(TraceSlice::startNanos))
    }

    private fun close(tid: Int, endNanos: Long, stack: ArrayDeque<OpenSlice>, roots: MutableList<TraceSlice>) {
        val open = stack.removeLastOrNull()
        if (open == null) {
            unopened++
            return
        }
        val slice = TraceSlice(open.name, tid, open.startNanos, endNanos, open.depth, open.children.toList())
        when (val enclosing = stack.lastOrNull()) {
            null -> roots.add(slice)
            else -> enclosing.children.add(slice)
        }
    }

    /**
     * Discards the slices still open when the thread's events run out, keeping the ones that closed.
     */
    private fun drain(stack: ArrayDeque<OpenSlice>, roots: MutableList<TraceSlice>) {
        while (stack.isNotEmpty()) {
            val open = stack.removeLast()
            unclosed++
            when (val enclosing = stack.lastOrNull()) {
                null -> roots.addAll(open.children)
                else -> enclosing.children.addAll(open.children)
            }
        }
    }

    private fun flatten(slice: TraceSlice): List<TraceSlice> =
        listOf(slice) + slice.children.flatMap(::flatten)
}
