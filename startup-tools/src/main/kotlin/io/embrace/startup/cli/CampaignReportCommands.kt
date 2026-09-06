package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.analysis.Campaign
import io.embrace.startup.analysis.CrossDeviceSections
import io.embrace.startup.analysis.FactorsReport
import io.embrace.startup.analysis.HypothesisTests
import java.nio.file.Path

/**
 * The three file-only campaign reports. They read what `variance --json` and `outlier-factors`
 * wrote, so they need no trace processor - only the campaign directory.
 *
 * `--little-cpus` replaces the Python's `LITTLE_CPUS` environment variable: take the value from
 * `probe`; the default (cpu0-3) is wrong on any device whose little cluster sits elsewhere and it
 * fails silently by reporting a meaningless share.
 */
private fun CliktCommand.littleCpusOption() = option(
    "--little-cpus",
    help = "comma-separated cpu ids of the little cluster (from `probe`; default 0,1,2,3)",
).default("0,1,2,3")

private fun parseCpus(text: String): Set<Int> = text.split(",").map { it.trim().toInt() }.toSet()

/** `hypothesis-tests` - the former `hypothesis_tests.py`. */
class HypothesisTestsCommand : CliktCommand(name = "hypothesis-tests") {

    private val campaignDir by argument("campaign-dir", help = "directory with pass1.json..passN.json and campaign.log")
        .path(mustExist = true, canBeFile = false)
    private val littleCpus by littleCpusOption()

    override fun help(context: Context): String =
        "Cross-pass hypothesis tests for one device's campaign (H1 placement, H2 pass alternation, H3 config-load " +
            "bimodality, H4 off-window fluctuators). Run per device BEFORE any cross-device or cross-arm comparison."

    override fun run() {
        val passes = Campaign.variancePasses(campaignDir)
        if (passes.isEmpty()) {
            echo("no passN.json files found", err = true)
            throw ProgramResult(1)
        }
        val temps = HypothesisTests.parseTemps(campaignDir.resolve("campaign.log"))
        echo(HypothesisTests.report(passes, temps, parseCpus(littleCpus)), trailingNewline = false)
    }
}

/** `factors-report` - the former `factors_report.py`. */
class FactorsReportCommand : CliktCommand(name = "factors-report") {

    private val campaignDir by argument("campaign-dir", help = "directory with pass1-factors.json..passN-factors.json")
        .path(mustExist = true, canBeFile = false)
    private val littleCpus by littleCpusOption()

    override fun help(context: Context): String =
        "Correlate on-device factors with window slowness, outlier-first: pass means, pooled correlations, the " +
            "outlier catalogue, extreme-outlier detail and blocked-function totals."

    override fun run() {
        val passes = Campaign.factorsPasses(campaignDir)
        if (passes.isEmpty()) {
            echo("no passN-factors.json found", err = true)
            throw ProgramResult(1)
        }
        echo(FactorsReport.report(passes, parseCpus(littleCpus)), trailingNewline = false)
    }
}

/** `cross-device-sections` - the former `cross_device_sections.py`. */
class CrossDeviceSectionsCommand : CliktCommand(name = "cross-device-sections") {

    private val devices by argument(
        "label=dir",
        help = "one or more <label>=<campaign-dir>; dirs sharing a label are pooled",
    ).multiple(required = true)

    override fun help(context: Context): String =
        "Side-by-side per-section median / max / share of window across devices - the workload-identity check. " +
            "Compare shares, not absolute ms."

    override fun run() {
        val grouped = LinkedHashMap<String, MutableList<Path>>()
        devices.forEach { arg ->
            val label = arg.substringBefore("=")
            val dir = arg.substringAfter("=", "")
            if (label.isEmpty() || dir.isEmpty()) {
                echo("expected <label>=<dir>, got '$arg'", err = true)
                throw ProgramResult(2)
            }
            grouped.getOrPut(label) { ArrayList() }.add(Path.of(dir))
        }
        val data = grouped.mapValues { Campaign.pooledVariance(it.value) }
        echo(CrossDeviceSections.report(data), trailingNewline = false)
    }
}
