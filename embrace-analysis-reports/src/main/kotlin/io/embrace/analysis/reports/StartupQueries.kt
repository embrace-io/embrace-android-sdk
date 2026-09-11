package io.embrace.analysis.reports

/**
 * The SQL the startup reports run against a trace, shipped verbatim as resources under `sql/`.
 * Keeping the text byte-identical is what lets the trace goldens compare raw engine output rather
 * than interpretations, so a change here is a change to what every golden means.
 *
 * These are STARTUP queries - they know the `emb-*` slice names and the SDK's section tree - which
 * is why they live beside the reports that use them and not with the engine client in `perfetto`,
 * which stays free of any knowledge of what is being measured.
 */
object StartupQueries {

    /** `analyze`: sections, window (+source), TTID, main-thread wait/run/cpus. */
    val STARTUP_METRICS: String by lazy { resource("startup_metrics.sql") }

    /** `variance`: window, per-CPU residency, first-occurrence sections, per-section thread states. */
    val VARIANCE_METRICS: String by lazy { resource("variance_metrics.sql") }

    /** `outlier-factors`: the external-factor catalogue inside the window. */
    val OUTLIER_METRICS: String by lazy { resource("outlier_metrics.sql") }

    /** Main-thread scheduling over the `emb-modules-init` window; a standalone deep dive on one trace. */
    val INIT_WINDOW_SCHED: String by lazy { resource("init_window_sched.sql") }

    /** Foreign-process GC overlap, bucketed by predicate strength; a standalone deep dive on one trace. */
    val FOREIGN_GC_OVERLAP: String by lazy { resource("foreign_gc_overlap.sql") }

    private fun resource(name: String): String =
        checkNotNull(StartupQueries::class.java.getResourceAsStream("sql/$name")) { "missing SQL resource $name" }
            .bufferedReader().use { it.readText() }
}
