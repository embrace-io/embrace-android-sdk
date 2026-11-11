package io.embrace.analysis.cli

import io.embrace.analysis.perfetto.TraceProcessorShell
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path

/**
 * `trace-processor`: prints the path of the trace_processor_shell release the version catalog pins, which
 * [installTraceProcessor] downloads on first use, for running the skill's standalone queries.
 */
class TraceProcessorCommand(
    private val out: PrintStream,
    private val err: PrintStream,
    private val installTraceProcessor: () -> Path = { TraceProcessorShell(log = err).install() },
) : Command {

    override val name: String = "trace-processor"

    override val summary: String = "print the path of the pinned trace_processor_shell, downloading it on first use"

    /** Returns 0, 1 when the binary can't be installed, or 2 for any argument but `--help`. */
    override fun run(args: List<String>): Int {
        if (args == listOf("--help") || args == listOf("-h")) {
            out.print(USAGE)
            return 0
        }
        if (args.isNotEmpty()) {
            err.println("$name: takes no arguments")
            err.print(USAGE)
            return 2
        }
        return try {
            out.println(installTraceProcessor())
            0
        } catch (e: IllegalStateException) {
            err.println("$name: ${e.message}")
            1
        } catch (e: IOException) {
            err.println("$name: ${e.message}")
            1
        }
    }

    private companion object {
        val USAGE = """
            |usage: startup-tools trace-processor
            |
            |Prints the path of Perfetto's trace_processor_shell at the version the version catalog pins
            |(perfettoTraceProcessor in gradle/libs.versions.toml), downloading it on first use.
            |
        """.trimMargin()
    }
}
