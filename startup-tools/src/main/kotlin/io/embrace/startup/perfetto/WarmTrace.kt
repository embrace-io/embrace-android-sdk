package io.embrace.startup.perfetto

import io.embrace.startup.core.proc.Processes
import java.io.Closeable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * One trace loaded ONCE into a background `trace_processor_shell server unix` session, answering
 * any number of `query --remote` calls without re-parsing.
 *
 * The naive model parses every trace once per query: ingest ran health, window and signals as three
 * processes per trace - three full parses. On a 200-trace leg that is 600 parses instead of 200. The
 * output format of `query --remote` is the same CSV `-q` prints, so rows are interchangeable; a
 * parity test asserts it. Always [close] (or use [TraceProcessor.withWarmTrace]) - a leaked session
 * lives until its idle timeout and holds the trace's memory.
 */
class WarmTrace private constructor(
    private val binary: Path,
    val trace: Path,
    private val name: String,
    private val timeout: Duration,
) : Closeable {

    /** Same contract as [TraceProcessor.queryRaw], against the warm session. */
    fun queryRaw(sql: String): TraceProcessor.Output {
        val sqlFile = Files.createTempFile("startup-tools-warm-", ".sql")
        try {
            Files.writeString(sqlFile, sql)
            val out = run(listOf(binary.toString(), "query", "--remote", name, "-f", sqlFile.toString()), timeout)
            if (out.exitCode != 0) {
                throw TraceProcessor.QueryFailed("warm query failed (exit ${out.exitCode}) on $trace:\n${out.stderr}")
            }
            return out
        } finally {
            Files.deleteIfExists(sqlFile)
        }
    }

    fun rows(sql: String): List<List<String>> = io.embrace.startup.core.text.Csv.parse(queryRaw(sql).stdout)

    fun triples(sql: String): List<TraceProcessor.Triple> = TraceProcessor.triplesOf(rows(sql), trace)

    /**
     * A session that will not die holds this trace's whole parse in memory until its idle timeout, and on
     * a 200-trace ingest that compounds, so a failure here is reported rather than swallowed. It is still
     * not thrown: this runs from `use`, where throwing would replace the caller's own exception.
     */
    override fun close() {
        val kill = listOf(binary.toString(), "server", "kill", name)
        val result = runCatching { run(kill, Duration.ofSeconds(KILL_TIMEOUT_S)) }
        val failure = result.exceptionOrNull()?.message ?: result.getOrNull()?.takeIf { it.exitCode != 0 }?.stderr
        if (failure != null) {
            System.err.println("warning: warm session $name for $trace did not stop; it holds memory until idle: $failure")
        }
    }

    companion object {
        private const val KILL_TIMEOUT_S = 30L
        private const val START_TIMEOUT_S = 600L

        /** Start a daemonised session on [trace]; returns once the server reports its socket. */
        fun open(binary: Path, trace: Path, timeout: Duration): WarmTrace {
            val name = "startup-tools-" + UUID.randomUUID().toString().take(NAME_CHARS)
            val out = run(
                listOf(binary.toString(), "server", "unix", "--name", name, "--daemonize", trace.toString()),
                Duration.ofSeconds(START_TIMEOUT_S),
            )
            if (out.exitCode != 0 || "socket-path=" !in out.stdout) {
                throw IOException("could not start a warm trace session for $trace (exit ${out.exitCode}):\n${out.stderr}")
            }
            return WarmTrace(binary, trace, name, timeout)
        }

        private const val NAME_CHARS = 8

        /** Through the shared helper, so this path gets the same deadlock and timeout guarantees. */
        private fun run(command: List<String>, timeout: Duration): TraceProcessor.Output {
            val out = try {
                Processes.run(command, timeout = timeout)
            } catch (e: Processes.TimedOut) {
                throw TraceProcessor.QueryFailed("${command.getOrNull(1)} timed out after $timeout", e)
            }
            return TraceProcessor.Output(out.exitCode, out.stdout, out.stderr)
        }
    }
}
