package io.embrace.analysis.records.store

/**
 * The short inline queries `ingest` runs to admit a trace: a named slice's window, the composed window
 * that predates the named slice, and the inventory of `emb-*` slice names. Keeping the text
 * byte-identical is what lets the trace goldens compare raw engine output rather than interpretations.
 *
 * These know the SDK's slice names, which is why they live with the records rather than with the
 * Perfetto client; the report queries live with the reports, in `analysis.StartupQueries`.
 */
object IngestQueries {

    /** The window query: the LAST positive-duration slice with this name, in ms. */
    fun window(slice: String): String = """
WITH win AS (
  SELECT s.ts AS ts, s.dur AS dur FROM slice s
  WHERE s.name = '$slice' AND s.dur > 0
  ORDER BY s.ts DESC LIMIT 1
)
SELECT (SELECT dur / 1e6 FROM win) AS window_ms;
"""

    /**
     * The composed-window query: first `emb-modules-init` start to the first `emb-post-services-setup`
     * end at or after it - the only window available before 9.2.0.
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

    /** The signals query: every distinct `emb-*` slice name the trace contains. */
    const val SIGNALS: String = """
SELECT DISTINCT name FROM slice WHERE name GLOB 'emb-*' ORDER BY name;
"""

    /** The sentinel `recipe.instrument` value meaning [COMPOSED_WINDOW] rather than a named slice. */
    const val COMPOSED_INSTRUMENT: String = "composed"

    /** SQL for the instrument recorded in a reference set: a slice name, or the composed sentinel. */
    fun windowFor(instrument: String): String =
        if (instrument == COMPOSED_INSTRUMENT) COMPOSED_WINDOW else window(instrument)
}
