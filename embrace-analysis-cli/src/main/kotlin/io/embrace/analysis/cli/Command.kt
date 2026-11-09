package io.embrace.analysis.cli

/** A `startup-tools` command, run by its [name] with the arguments after it. */
interface Command {

    /** The name the command is run by. */
    val name: String

    /** One line on what the command does, for the tools' usage. */
    val summary: String

    /** Runs the command with [args] and returns the process exit code. */
    fun run(args: List<String>): Int
}
