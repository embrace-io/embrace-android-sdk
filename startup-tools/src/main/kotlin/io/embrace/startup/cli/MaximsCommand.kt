package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.core.repo.RepoRoot
import io.embrace.startup.store.MaximsRunner
import java.nio.file.Path

/**
 * `maxims`: score a campaign directory against every maxim and record the verdicts in the ledger;
 * render MAXIMS.md from the definitions and the ledger. The default ledger and document live in the
 * committed records root (`maxims/` under [RepoRoot.records]). Recording a run also packs its per-pass
 * datasets and log into `campaigns/<run-id>.zip` in the same root: the traces they were derived from are
 * wiped by the next benchmark, so the datasets are the durable evidence behind the ledger, and an archive
 * that is written once and never edited is one compressed file rather than a directory to version.
 */
class MaximsCommand : CliktCommand(name = "maxims") {
    init {
        subcommands(Score(), Render())
    }

    override fun help(context: Context): String =
        "The bench's beliefs about SDK init, checked on every campaign and accumulated: `score` a campaign directory " +
            "against every maxim into the ledger, `render` MAXIMS.md from the ledger."

    override fun run() = Unit

    private class Score : CliktCommand(name = "score") {
        private val campaignDir by argument("campaign", help = "a campaign directory, or a campaigns/<run-id>.zip from the records root")
            .path(mustExist = true)
        private val referenceSet by option("--reference-set", help = "reference-set.json, to map the run's serial to a device_key")
            .path(mustExist = true)
        private val deviceKey by option("--device-key", help = "override when provenance cannot identify the device")
        private val sdkVersion by option("--sdk-version", help = "override when provenance does not record the SDK version")
        private val arm by option("--arm", help = "override the arm label (default: the provenance's compile level, else 'default')")
        private val ledger by option(
            "--ledger",
            help = "ledger JSON to update (default: ${MaximsRunner.DEFAULT_LEDGER_REL} under the repo); " +
                "'none' for a rerun that must not count twice",
        )
        private val runId by option("--run-id", help = "defaults to the campaign directory name")
        private val now by option("--now", help = "timestamp to stamp (tests)")
        private val records by option(
            "--records",
            help = "records root whose campaigns/<run-id>.zip keeps the run's datasets and log once it is recorded " +
                "(default: ${RepoRoot.RECORDS_REL} under the repo); 'none' to keep nothing",
        )

        override fun help(context: Context): String =
            "Score one campaign directory against every maxim and record the verdicts."

        override fun run() {
            val recordsRoot = when (records) {
                null -> RepoRoot.records()
                "none" -> null
                else -> Path.of(records.orEmpty())
            }
            val text = try {
                MaximsRunner.score(
                    campaign = campaignDir,
                    referenceSet = referenceSet,
                    deviceKey = deviceKey,
                    sdkVersion = sdkVersion,
                    arm = arm,
                    ledger = ledger ?: MaximsRunner.defaultLedger().toString(),
                    runId = runId,
                    now = now,
                    recordsRoot = recordsRoot,
                )
            } catch (e: IllegalArgumentException) {
                echo(e.message, err = true)
                throw ProgramResult(1)
            }
            echo(text, trailingNewline = false)
        }
    }

    private class Render : CliktCommand(name = "render") {
        private val ledger by option(
            "--ledger",
            help = "ledger JSON to read (default: ${MaximsRunner.DEFAULT_LEDGER_REL} under the repo); 'none' for an empty one",
        )
        private val output by option("-o", "--output", help = "MAXIMS.md to write (default: beside the ledger); '-' for stdout")
        private val now by option("--now", help = "timestamp to stamp (tests)")

        override fun help(context: Context): String =
            "Render MAXIMS.md from the maxim definitions and the ledger."

        override fun run() {
            echo(
                MaximsRunner.render(ledger ?: MaximsRunner.defaultLedger().toString(), output ?: MaximsRunner.defaultDoc().toString(), now),
                trailingNewline = false,
            )
        }
    }
}
