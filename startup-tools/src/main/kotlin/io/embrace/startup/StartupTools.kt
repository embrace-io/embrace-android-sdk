package io.embrace.startup

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.versionOption

/**
 * The dispatcher. Each analysis is one subcommand in its own file under `cli/`, deliberately kept
 * "script-sized": to change what an analysis does, edit one file. Everything the subcommands share
 * lives once under `core/`, `perfetto/`, `device/` and `campaign/`.
 *
 * Subcommands are registered in [main]; that list is the complete set of tools. `README.md` maps each
 * one to the script it replaced, for reading analysis documents written before the port.
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
