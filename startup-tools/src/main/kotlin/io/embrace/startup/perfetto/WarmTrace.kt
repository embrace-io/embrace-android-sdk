package io.embrace.startup.perfetto

import java.io.Closeable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * One trace loaded ONCE into a background `trace_processor_shell server unix` session, answering
 * any number of `query --remote` calls without re-parsing.
 *
 * The Python parsed every trace once per query: ingest ran health, window and signals as three
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

    override fun close() {
        runCatching { run(listOf(binary.toString(), "server", "kill", name), Duration.ofSeconds(KILL_TIMEOUT_S)) }
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

        private fun run(command: List<String>, timeout: Duration): TraceProcessor.Output {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            val stderr = StringBuilder()
            val drain = Thread { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } }
            drain.start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw TraceProcessor.QueryFailed("${command.getOrNull(1)} timed out after $timeout")
            }
            drain.join()
            return TraceProcessor.Output(process.exitValue(), stdout, stderr.toString())
        }
    }
}
