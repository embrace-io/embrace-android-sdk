package io.embrace.analysis

import io.embrace.analysis.cli.TraceProcessorCommand
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * The startup-analysis tools, run through `tools/startup <command> [args...]`. Output is UTF-8 whatever the platform
 * default.
 */
fun main(args: Array<String>) {
    val out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    val err = PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8)
    val tools = StartupTools(listOf(TraceProcessorCommand(out, err)), out, err)
    exitProcess(tools.run(args.toList()))
}
