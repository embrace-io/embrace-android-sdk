package io.embrace.analysis.startup

import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.TraceProcessor

/** Reads [StartupMetrics] from loaded traces, through the views of `startup_views.sql`. */
class StartupMetricsReader {

    private val views: String =
        checkNotNull(StartupMetricsReader::class.java.getResource(VIEWS_RESOURCE)) { "missing $VIEWS_RESOURCE" }.readText()

    /** The metrics of the trace loaded in [session], defining the views in it first. */
    fun read(session: TraceProcessor.Session): StartupMetrics {
        session.query(views)
        return fromRows(
            sections = session.query(SECTIONS),
            window = session.query(WINDOW).singleOrNull(),
            ttid = session.query(TTID).singleOrNull(),
        )
    }

    /**
     * The metrics the queries' rows hold: [sections] with each section's `name` and `dur`, and the single [window]
     * (`dur`, `src`, and the init thread's `wait`, `running` and `cpus`) and [ttid] (`dur`) rows, each null when the
     * trace has none. Times are in ns. Throws [IllegalArgumentException] for a window source the views don't produce.
     */
    internal fun fromRows(sections: Sequence<Row>, window: Row?, ttid: Row?): StartupMetrics = StartupMetrics(
        sections = sections.associateTo(LinkedHashMap()) { it.string("name") to it.long("dur").toMillis() },
        window = window?.let(::startupWindow),
        ttidMs = ttid?.long("dur")?.toMillis(),
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
        const val NANOS_PER_MILLI = 1e6
    }
}
