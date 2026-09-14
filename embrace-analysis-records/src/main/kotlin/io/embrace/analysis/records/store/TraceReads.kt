package io.embrace.analysis.records.store

import io.embrace.analysis.perfetto.TraceProcessor
import java.nio.file.Path

/**
 * The two single-column reads the `ingest` command makes per trace, with their exact (deliberately
 * naive) parses: the window value is the LAST line of stdout that parses as a number, and the signal
 * inventory is every line that, once stripped of quotes, starts with `emb-`.
 */
object TraceReads {

    /** Window duration in ms for `instrument` (a slice name or [IngestQueries.COMPOSED_INSTRUMENT]), or null. */
    fun windowMs(tp: TraceProcessor, trace: Path, instrument: String): Double? =
        parseWindow(tp.queryRaw(IngestQueries.windowFor(instrument), trace).stdout)

    /** Every distinct `emb-*` slice name in the trace, sorted. */
    fun signals(tp: TraceProcessor, trace: Path): List<String> =
        parseSignals(tp.queryRaw(IngestQueries.SIGNALS, trace).stdout)

    fun parseWindow(stdout: String): Double? =
        stdout.trim().lines().asReversed().firstNotNullOfOrNull { it.trim().trim('"').toDoubleOrNull() }

    fun parseSignals(stdout: String): List<String> =
        stdout.lines().map { it.trim().trim('"') }.filter { it.startsWith("emb-") }.toSortedSet().toList()
}
