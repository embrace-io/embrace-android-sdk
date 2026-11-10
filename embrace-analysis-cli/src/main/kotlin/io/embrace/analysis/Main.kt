package io.embrace.analysis

import io.embrace.analysis.cli.AnalyzeCommand
import io.embrace.analysis.cli.TraceProcessorCommand
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * The startup-analysis tools, run through `tools/startup <command> [args...]`, which sets `STARTUP_TOOLS_REPO` to its
 * checkout. Output is UTF-8 whatever the platform default, since the reports print `—` and `↳`. The commands write
 * their output to standard output through their own stream; `System.out` goes to standard error, so what libraries
 * print to it, such as the trace processor's status lines, stays out of the command's output.
 */
fun main(args: Array<String>) {
    val out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    val err = PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8)
    System.setOut(err)
    val repo = System.getenv("STARTUP_TOOLS_REPO")?.let { Paths.get(it) } ?: Paths.get("").toAbsolutePath()
    val tools = StartupTools(listOf(AnalyzeCommand(out, err, repo), TraceProcessorCommand(out, err)), out, err)
    exitProcess(tools.run(args.toList()))
}
