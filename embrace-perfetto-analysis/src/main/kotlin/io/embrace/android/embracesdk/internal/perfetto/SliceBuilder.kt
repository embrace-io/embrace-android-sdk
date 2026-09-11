package io.embrace.android.embracesdk.internal.perfetto

private const val BEGIN_PREFIX = "B|"
private const val END = "E"
private const val END_PREFIX = "E|"
private const val SEPARATOR = '|'

/** `B|<tgid>|<name>`, where the name may itself contain a separator. */
private const val BEGIN_PARTS = 3

/**
 * Pairs begin and end events into slices, keeping one stack per thread.
 *
 * [events] is sorted by timestamp first: ftrace batches events per CPU, so a trace hands them over
 * interleaved, and stacking them as they arrive would nest slices under the wrong parent.
 */
internal fun buildSlices(events: List<AtraceEvent>): PerfettoTrace {
    val stacks = mutableMapOf<Int, ArrayDeque<OpenSlice>>()
    val slices = mutableListOf<TraceSlice>()
    var unmatchedEnds = 0
    var ignored = 0

    events.sortedBy(AtraceEvent::timestampNanos).forEach { event ->
        val payload = event.payload.trimPayload()
        when {
            payload.startsWith(BEGIN_PREFIX) -> {
                val stack = stacks.getOrPut(event.tid, ::ArrayDeque)
                stack.addLast(OpenSlice(payload.sectionName(), event.timestampNanos, stack.size))
            }

            // both `E` and `E|<tgid>` end a slice, and traces contain both forms
            payload == END || payload.startsWith(END_PREFIX) -> {
                val open = stacks[event.tid]?.removeLastOrNull()
                if (open == null) {
                    unmatchedEnds++
                } else {
                    slices += TraceSlice(open.name, event.tid, open.startNanos, event.timestampNanos, open.depth)
                }
            }

            // async begin/end, counters, and anything else atrace can write
            else -> ignored++
        }
    }

    return PerfettoTrace(
        slices = slices.sortedWith(compareBy(TraceSlice::startNanos, TraceSlice::tid, TraceSlice::depth)),
        unmatchedEndCount = unmatchedEnds,
        unclosedBeginCount = stacks.values.sumOf(ArrayDeque<OpenSlice>::size),
        ignoredEventCount = ignored,
    )
}

/** Drops the line terminator and any trailing padding atrace writes after the payload. */
private fun String.trimPayload(): String = substringBefore('\n').substringBefore(Char.MIN_VALUE)

/** Returns the section name in a begin payload, or an empty name when it carries none. */
private fun String.sectionName(): String =
    split(SEPARATOR, limit = BEGIN_PARTS).getOrElse(BEGIN_PARTS - 1) { "" }
