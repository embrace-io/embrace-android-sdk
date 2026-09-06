package io.embrace.startup.perfetto

/**
 * The SQL the skills run, unchanged. File-backed queries ship as resources under `sql/` (verbatim
 * copies of the `.sql` files the commands used); the short inline queries from the `ingest`
 * command live here as constants. Keeping the text byte-identical is what lets the trace
 * goldens compare raw output rather than interpretations.
 */
object Queries {

    /** `analyze`: sections, window (+source), TTID, main-thread wait/run/cpus. */
    val STARTUP_METRICS: String by lazy { resource("startup_metrics.sql") }

    /** `variance`: window, per-CPU residency, first-occurrence sections, per-section thread states. */
    val VARIANCE_METRICS: String by lazy { resource("variance_metrics.sql") }

    /** `outlier-factors`: the external-factor catalogue inside the window. */
    val OUTLIER_METRICS: String by lazy { resource("outlier_metrics.sql") }

    /** Main-thread scheduling over the `emb-modules-init` window. */
    val INIT_WINDOW_SCHED: String by lazy { resource("init_window_sched.sql") }

    /** Foreign-process GC overlap, bucketed by predicate strength. */
    val FOREIGN_GC_OVERLAP: String by lazy { resource("foreign_gc_overlap.sql") }

    /** `ingest`'s window query: the LAST positive-duration slice with this name, in ms. */
    fun window(slice: String): String = """
WITH win AS (
  SELECT s.ts AS ts, s.dur AS dur FROM slice s
  WHERE s.name = '$slice' AND s.dur > 0
  ORDER BY s.ts DESC LIMIT 1
)
SELECT (SELECT dur / 1e6 FROM win) AS window_ms;
"""

    /**
     * `ingest`'s composed-window query: first `emb-modules-init` start to the first
     * `emb-post-services-setup` end at or after it - the only window available before 9.2.0.
     */
    const val COMPOSED_WINDOW: String = """
WITH m AS (
  SELECT MIN(s.ts) AS ts FROM slice s WHERE s.name = 'emb-modules-init' AND s.dur > 0
),
p AS (
  SELECT MIN(s.ts + s.dur) AS te FROM slice s
   WHERE s.name = 'emb-post-services-setup' AND s.dur > 0
     AND s.ts >= (SELECT ts FROM m)
)
SELECT CASE WHEN (SELECT ts FROM m) IS NULL OR (SELECT te FROM p) IS NULL THEN NULL
            ELSE ((SELECT te FROM p) - (SELECT ts FROM m)) / 1e6 END AS window_ms;
"""

    /** `ingest`'s signals query: every distinct `emb-*` slice name the trace contains. */
    const val SIGNALS: String = """
SELECT DISTINCT name FROM slice WHERE name GLOB 'emb-*' ORDER BY name;
"""

    /** The sentinel `recipe.instrument` value meaning [COMPOSED_WINDOW] rather than a named slice. */
    const val COMPOSED_INSTRUMENT: String = "composed"

    /** SQL for the instrument recorded in a reference set: a slice name, or the composed sentinel. */
    fun windowFor(instrument: String): String =
        if (instrument == COMPOSED_INSTRUMENT) COMPOSED_WINDOW else window(instrument)

    private fun resource(name: String): String =
        checkNotNull(Queries::class.java.getResourceAsStream("sql/$name")) { "missing SQL resource $name" }
            .bufferedReader().use { it.readText() }
}
