package io.embrace.analysis.startup

import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.TraceProcessor
import io.embrace.analysis.benchmark.CaptureProfileClassifier

/** Reads [StartupMetrics] from loaded traces, through the views of `startup_views.sql`. */
class StartupMetricsReader {

    private val captureClassifier = CaptureProfileClassifier()

    private val views: String =
        checkNotNull(StartupMetricsReader::class.java.getResource(VIEWS_RESOURCE)) { "missing $VIEWS_RESOURCE" }.readText()

    /** The metrics of the trace loaded in [session], defining the views in it first. */
    fun read(session: TraceProcessor.Session): StartupMetrics {
        session.query(views)
        return fromRows(
            sections = session.query(SECTIONS),
            window = session.query(WINDOW).singleOrNull(),
            ttid = session.query(TTID).singleOrNull(),
            counters = session.query(COUNTERS),
            capture = session.query(CAPTURE).single(),
            config = session.query(CONFIG).singleOrNull(),
        )
    }

    /**
     * The metrics the queries' rows hold: [sections] with each section's `name` and `dur`, and the single [window]
     * (`dur`, `src`, and the init thread's `wait`, `running` and `cpus`) and [ttid] (`dur`) rows, each null when the
     * trace has none; [counters], the non-zero `stats` counters (`name`, `idx`, `severity`, `value`); and [capture], the
     * one row on what the capture covers (`sched_rows`, `sched_start`, `window_start`, `loss_reported`,
     * `buffer_wraps`, `stats_after_data`); and [config], the trace's recorded config (`str_value`, the TraceConfig as
     * text), null when it records none. Times are in ns. Throws [IllegalArgumentException] for a window source or
     * counter severity the views don't produce.
     */
    internal fun fromRows(
        sections: Sequence<Row>,
        window: Row?,
        ttid: Row?,
        counters: Sequence<Row>,
        capture: Row,
        config: Row?,
    ): StartupMetrics {
        val startupWindow = window?.let(::startupWindow)
        return StartupMetrics(
            sections = sections.associateTo(LinkedHashMap()) { it.string("name") to it.long("dur").toMillis() },
            window = startupWindow,
            ttidMs = ttid?.long("dur")?.toMillis(),
            health = traceHealth(counters.toList(), capture, hasWindow = startupWindow != null),
            capture = captureClassifier.classify(config?.nullableString("str_value")),
        )
    }

    /**
     * The trace's health. Data was lost when trace_processor counts a data loss, or, when the buffer stats don't count
     * the data a wrap overwrote before it was read, when a buffer wrapped at all: without a wrap, nothing was
     * overwritten. Lost data with no window means the loss took the window; no window without loss means it was never
     * recorded.
     */
    private fun traceHealth(counters: List<Row>, capture: Row, hasWindow: Boolean): TraceHealth {
        val bySeverity = counters.groupBy { row ->
            val severity = row.string("severity")
            require(severity == DATA_LOSS || severity == ERROR) { "unexpected counter severity '$severity'" }
            severity
        }
        val losses = bySeverity[DATA_LOSS].orEmpty().map(::statCounter)
        val lossReported = capture.long("loss_reported") != 0L
        val bufferWraps = capture.long("buffer_wraps")
        val lost = losses.isNotEmpty() || (!lossReported && bufferWraps > 0)
        val verdict = when {
            lost && !hasWindow -> TraceHealth.Verdict.UNUSABLE
            !hasWindow -> TraceHealth.Verdict.NO_WINDOW
            lost -> TraceHealth.Verdict.LOSSY
            else -> TraceHealth.Verdict.OK
        }
        return TraceHealth(
            verdict = verdict,
            losses = losses,
            importErrors = bySeverity[ERROR].orEmpty().map(::statCounter),
            lossCountersReported = lossReported,
            bufferWraps = bufferWraps,
            statsAfterData = capture.long("stats_after_data") != 0L,
            scheduling = schedulingCoverage(capture),
        )
    }

    /** How much of the window the scheduling covers: from where every CPU's scheduling data is complete. */
    private fun schedulingCoverage(capture: Row): TraceHealth.SchedulingCoverage {
        val schedStart = capture.nullableLong("sched_start")
        val windowStart = capture.nullableLong("window_start")
        return when {
            capture.long("sched_rows") == 0L -> TraceHealth.SchedulingCoverage.NONE
            schedStart == null || windowStart == null -> TraceHealth.SchedulingCoverage.UNKNOWN
            schedStart <= windowStart -> TraceHealth.SchedulingCoverage.FULL
            else -> TraceHealth.SchedulingCoverage.PARTIAL
        }
    }

    private fun statCounter(row: Row) = TraceHealth.StatCounter(
        name = row.string("name"),
        buffer = row.nullableLong("idx")?.toInt(),
        value = row.long("value"),
    )

    private fun startupWindow(row: Row): StartupWindow {
        val key = row.string("src")
        return StartupWindow(
            durationMs = row.long("dur").toMillis(),
            source = requireNotNull(StartupWindow.Source.entries.firstOrNull { it.key == key }) {
                "unknown window source '$key'"
            },
            initThread = StartupWindow.ThreadScheduling(
                runningMs = row.long("running").toMillis(),
                waitMs = row.long("wait").toMillis(),
                cpus = row.long("cpus").toInt(),
            ),
        )
    }

    private fun Long.toMillis(): Double = this / NANOS_PER_MILLI

    private companion object {
        const val VIEWS_RESOURCE = "startup_views.sql"
        const val SECTIONS = "SELECT name, dur FROM emb_first_sections ORDER BY name"
        const val WINDOW = "SELECT dur, src, wait, running, cpus FROM emb_startup_window"
        const val TTID = "SELECT dur FROM android_startups ORDER BY ts LIMIT 1"
        const val COUNTERS = "SELECT name, idx, severity, value FROM emb_trace_counters ORDER BY severity, name, idx"
        const val CAPTURE =
            "SELECT sched_rows, sched_start, window_start, loss_reported, buffer_wraps, stats_after_data FROM emb_trace_capture"
        const val CONFIG = "SELECT str_value FROM metadata WHERE name = 'trace_config_pbtxt'"
        const val DATA_LOSS = "data_loss"
        const val ERROR = "error"
        const val NANOS_PER_MILLI = 1e6
    }
}
