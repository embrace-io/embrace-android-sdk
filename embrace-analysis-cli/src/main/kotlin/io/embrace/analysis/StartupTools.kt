package io.embrace.analysis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.versionOption

/**
 * The dispatcher. Each analysis is one subcommand in its own file under `cli/`, deliberately kept
 * "script-sized": to change what an analysis does, edit one file. Everything the subcommands share
 * lives once in the library modules (`embrace-analysis-common`, `-stats`, `-perfetto`, `-device`,
 * `-records`, `-reports`, `-maxims`, `-campaign`).
 *
 * Subcommands are registered in [main]; that list is the complete set of tools, and `README.md`
 * documents each one.
 */
class StartupTools : CliktCommand(name = "startup-tools") {
    init {
        versionOption(VERSION)
    }

    override fun help(context: Context): String =
        "SDK startup-analysis toolchain: run benchmarks, analyse perfetto traces, maintain the " +
            "longitudinal store, compare versions and devices."

    override fun run() = Unit
}
