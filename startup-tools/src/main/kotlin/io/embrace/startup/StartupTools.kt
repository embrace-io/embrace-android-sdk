package io.embrace.startup

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.versionOption

/**
 * The dispatcher. Every former Python script becomes one subcommand, each in its own file under
 * `cli/`, so the "script-sized" edit surface of the Python is preserved: to change what an analysis
 * does, edit one file. Everything the subcommands share lives once under `core/`, `perfetto/`,
 * `device/` and `campaign/`.
 *
 * Subcommands are registered in [main]. A command absent from that list has not been ported yet; the
 * Python remains the tool of record for it until it appears there.
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
