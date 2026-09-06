package io.embrace.startup.perfetto

import io.embrace.startup.core.text.Csv
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Runs SQL against one trace with the native `trace_processor_shell`, through the legacy flat
 * interface (`-q <file.sql> <trace>`) that every Python script used and that upstream documents as
 * "fully supported and will remain so". Output is CSV: a header row, then one row per result row,
 * with `[NULL]` for SQL NULL.
 *
 * Every call re-parses the trace. That is the Python's cost model too, and it is what the parity
 * goldens were produced with; the warm-session client (`server unix` + `query --remote`) is a
 * later phase behind this same interface.
 */
class TraceProcessor(
    val binary: Path,
    private val timeout: Duration = Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES),
) {

    class QueryFailed(message: String) : IOException(message)

    data class Output(val exitCode: Int, val stdout: String, val stderr: String)

    /** One `(what, k, val)` row of the skills' standard result shape; `value` is null for `[NULL]`. */
    data class Triple(val what: String, val k: String, val value: String?)

    /** Raw process output. Throws [QueryFailed] on a non-zero exit, with the tool's stderr. */
    fun queryRaw(sql: String, trace: Path): Output {
        val sqlFile = Files.createTempFile("startup-tools-", ".sql")
        try {
            Files.writeString(sqlFile, sql)
            val process = ProcessBuilder(binary.toString(), "-q", sqlFile.toString(), trace.toString())
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw QueryFailed("trace_processor timed out after $timeout on $trace")
            }
            val output = Output(process.exitValue(), stdout, stderr)
            if (output.exitCode != 0) {
                throw QueryFailed("trace_processor failed (exit ${output.exitCode}) on $trace:\n${output.stderr}")
            }
            return output
        } finally {
            Files.deleteIfExists(sqlFile)
        }
    }

    /** All CSV rows, header included, as strings (`[NULL]` left as-is). */
    fun rows(sql: String, trace: Path): List<List<String>> = Csv.parse(queryRaw(sql, trace).stdout)

    /**
     * Load [trace] once and run [block] against it (phase 6, warm sessions). Falls back to per-query
     * `-q` parses if the server cannot start, so callers never need a second code path.
     */
    fun <T> withWarmTrace(trace: Path, block: (QueryTarget) -> T): T {
        val warm = runCatching { WarmTrace.open(binary, trace, timeout) }.getOrNull()
        if (warm == null) return block(ColdTarget(this, trace))
        return warm.use { block(WarmTarget(it)) }
    }

    /** What a per-trace analysis needs: one query at a time against a fixed trace, warm or cold. */
    interface QueryTarget {
        val trace: Path
        fun queryRaw(sql: String): Output
        fun triples(sql: String): List<Triple> = triplesOf(Csv.parse(queryRaw(sql).stdout), trace)
    }

    private class ColdTarget(private val tp: TraceProcessor, override val trace: Path) : QueryTarget {
        override fun queryRaw(sql: String): Output = tp.queryRaw(sql, trace)
    }

    private class WarmTarget(private val warm: WarmTrace) : QueryTarget {
        override val trace: Path get() = warm.trace
        override fun queryRaw(sql: String): Output = warm.queryRaw(sql)
    }

    /**
     * `(what, k, val)` rows after the standard header, exactly as `analyze_startup.extract_metrics`
     * read them: rows before the header sentinel and rows without three fields are ignored; a
     * missing header is an error because it means the query returned nothing recognisable.
     */
    fun triples(sql: String, trace: Path): List<Triple> = triplesOf(rows(sql, trace), trace)

    companion object {
        const val NULL: String = "[NULL]"
        private const val DEFAULT_TIMEOUT_MINUTES = 15L
        private val HEADER = listOf("what", "k", "val")

        fun triplesOf(rows: List<List<String>>, trace: Path? = null): List<Triple> {
            val header = rows.indexOf(HEADER)
            if (header < 0) throw QueryFailed("no result header in trace_processor output for $trace")
            return rows.drop(header + 1)
                .filter { it.size == HEADER.size }
                .map { Triple(it[0], it[1], it[2].takeUnless { v -> v == NULL }) }
        }
    }
}
