package io.embrace.analysis.startup

import io.embrace.analysis.benchmark.IterationTrace
import io.embrace.analysis.perfetto.TraceProcessorServer
import java.nio.file.Path

/** Reads the [StartupMetrics] of iteration traces until it is closed. */
interface StartupMetricsSource : AutoCloseable {

    fun read(trace: IterationTrace): StartupMetrics
}

/** A [StartupMetricsSource] that reads each trace through one [TraceProcessorServer] running [shell]. */
class TraceProcessorStartupMetricsSource(shell: Path) : StartupMetricsSource {

    private val server = TraceProcessorServer(shell)
    private val reader = StartupMetricsReader()

    override fun read(trace: IterationTrace): StartupMetrics = server.query(trace.path) { reader.read(this) }

    override fun close() {
        server.close()
    }
}
