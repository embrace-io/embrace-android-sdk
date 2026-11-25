package io.embrace.analysis.startup

/**
 * Whether a trace can be trusted, decided before any number read from it is. Perfetto doesn't fail loudly when it
 * drops data: a ring buffer that wraps before its data is read evicts the oldest packets, which are the start of a
 * launch, and the trace still loads. So the counters trace_processor keeps in its `stats` table decide, together with
 * whether the SDK-init window survived.
 *
 * [losses] are the non-zero counters trace_processor classes as data loss, and [importErrors] those it classes as
 * errors, which name what it couldn't read but leave the durations it did read standing. [lossCountersReported] is
 * false when the buffer stats don't count the data a wrap overwrote before it was read, as some captures' stats and
 * older tracing services' don't: such a trace can't tell a harmless wrap from a lossy one, so its [bufferWraps] count
 * as loss. [statsAfterData] is false when the buffer stats were recorded before any data was written, so they can't
 * show a loss at all. [scheduling] says how much of the window the trace's scheduling data covers.
 */
data class TraceHealth(
    val verdict: Verdict,
    val losses: List<StatCounter>,
    val importErrors: List<StatCounter>,
    val lossCountersReported: Boolean,
    val bufferWraps: Long,
    val statsAfterData: Boolean,
    val scheduling: SchedulingCoverage,
) {

    /** A non-zero `stats` counter: its [name], the buffer it counts for ([buffer], null for a trace-wide one), and its [value]. */
    data class StatCounter(val name: String, val buffer: Int?, val value: Long)

    /** How far a trace can be trusted, by the [key] the report prints, with the [guidance] the report gives for it. */
    enum class Verdict(val key: String, val guidance: String) {
        OK(
            key = "ok",
            guidance = "nothing was lost.",
        ),
        LOSSY(
            key = "lossy",
            guidance = "data was lost, but the SDK-init window survived: its durations stand, but don't claim a section or " +
                "event is absent, or count anything over the whole trace, from these traces.",
        ),
        NO_WINDOW(
            key = "no-window",
            guidance = "no SDK-init window and no loss that could have evicted it: the wrong build, an SDK that didn't " +
                "start, or a capture without the app's sections. Enlarging buffers won't fix it.",
        ),
        UNUSABLE(
            key = "unusable",
            guidance = "data was lost and the SDK-init window with it: left out of every figure below. Keep the earliest " +
                "data (fill_policy DISCARD), enlarge or split the buffers, or capture less.",
        ),
    }

    /** How much of the SDK-init window the trace's scheduling data covers, by the [key] the report prints. */
    enum class SchedulingCoverage(val key: String) {

        /** From before the window starts. */
        FULL("full"),

        /** Only from inside the window: a kernel ring buffer wrapped and dropped its start. */
        PARTIAL("partial"),

        /** The trace has no scheduling data: the capture records none. */
        NONE("none"),

        /** The device's tracing service doesn't report where its scheduling data starts, or there is no window. */
        UNKNOWN("unknown"),
    }
}
