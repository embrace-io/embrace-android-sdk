package io.embrace.analysis

import io.embrace.analysis.cli.Command
import java.io.PrintStream

/** The startup-analysis tools: runs the one of [commands] named first in the arguments with the rest. */
class StartupTools(
    private val commands: List<Command>,
    private val out: PrintStream,
    private val err: PrintStream,
) {

    /** Runs the command [args] name and returns its exit code, or 2 for no or an unknown command. */
    fun run(args: List<String>): Int {
        val name = args.firstOrNull()
        if (name == "-h" || name == "--help") {
            out.print(usage())
            return 0
        }
        val command = commands.firstOrNull { it.name == name }
        if (command == null) {
            if (name != null) {
                err.println("startup-tools: unknown command '$name'")
            }
            err.print(usage())
            return 2
        }
        return command.run(args.drop(1))
    }

    private fun usage(): String = buildString {
        appendLine("usage: startup-tools <command> [args...]")
        appendLine()
        appendLine("commands:")
        val width = commands.maxOf { it.name.length } + COLUMN_GAP
        commands.forEach { appendLine("  ${it.name.padEnd(width)}${it.summary}") }
        appendLine()
        appendLine("Run `startup-tools <command> --help` for a command's options.")
    }

    private companion object {
        const val COLUMN_GAP = 3
    }
}
