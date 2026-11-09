package io.embrace.analysis.perfetto

import androidx.benchmark.traceprocessor.PerfettoTrace
import androidx.benchmark.traceprocessor.ServerLifecycleManager
import androidx.benchmark.traceprocessor.TraceProcessor
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/**
 * Perfetto's native `trace_processor_shell` at [shell], run as a server for AndroidX's [TraceProcessor] from
 * construction until [close], with one trace loaded at a time.
 */
class TraceProcessorServer(shell: Path) : AutoCloseable {

    private val server = TraceProcessor.startServer(
        serverLifecycleManager = ShellServer(shell),
        eventCallback = TraceProcessor.EventCallback.Noop,
        tracer = TraceProcessor.Tracer(),
    )

    // the trace processor refuses a trace path with a space in it, which benchmark output directories have
    private val links: Path = Files.createTempDirectory("trace-processor-traces")

    /** Loads [trace], runs [block] against it, and unloads it again. */
    fun <T> query(trace: Path, block: TraceProcessor.Session.() -> T): T {
        val link = Files.createSymbolicLink(links.resolve(LINK_NAME), trace.toAbsolutePath())
        try {
            return server.traceProcessor.loadTrace(PerfettoTrace(link.toString()), block)
        } finally {
            Files.deleteIfExists(link)
        }
    }

    override fun close() {
        server.close()
        Files.deleteIfExists(links)
    }

    /** Runs `trace_processor_shell` in HTTP server mode on a free local port. */
    private class ShellServer(private val shell: Path) : ServerLifecycleManager {

        private var process: Process? = null

        override fun start(): Int {
            val port = ServerSocket(0).use { it.localPort }
            process = ProcessBuilder(shell.toString(), "--httpd", "--http-port", port.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            return port
        }

        override fun timeoutMessage(): String = "$shell did not start serving trace processor queries"

        override fun stop() {
            process?.run {
                destroy()
                waitFor()
            }
            process = null
        }
    }

    private companion object {
        const val LINK_NAME = "loaded.perfetto-trace"
    }
}
