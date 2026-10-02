package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.campaign.FleetCampaign
import io.embrace.analysis.common.repo.RepoRoot

/**
 * `fleet-campaign`: N back-to-back passes on one device with the silicon cool gate. Flag-based,
 * matching the form `cell-runner` already calls it with.
 */
class FleetCampaignCommand : CliktCommand(name = "fleet-campaign") {

    private val serial by option("--serial", help = "adb serial of the target device").required()
    private val dirMatch by option(
        "--dir-match",
        help = "fragment of the device's model name (as macrobenchmark names its output dir); normalised match, sole-dir fallback",
    ).default("")
    private val out by option("--out", help = "campaign output directory").path().required()
    private val passes by option("--passes").int().required()
    private val method by option("--method", help = "StartupBenchmarks method (coldStartup, coldStartupNoAot, coldStartupBaselineProfile)")
        .default("coldStartup")
    private val iterations by option(
        "--iterations",
        help = "declared iterations per pass (recorded as the run shape; the harness must match)",
    )
        .int()
    private val gapAfterPass by option("--gap-after-pass", help = "sleep 300 s after this pass number").int()
    private val repo by option("--repo", help = "repo root (default: git rev-parse --show-toplevel)").path()
    private val dryRun by option(
        "--dry-run",
        help = "resolve paths, read thermal state and print the gradle command without running",
    ).flag()
    private val noVerifyCohort by option(
        "--no-verify-cohort",
        help = "do not arm the ExampleApp's logcat tap and classify each launch's user-session cohort (default: verify; " +
            "turn off only when comparing against runs made before the tap existed)",
    ).flag()

    override fun help(context: Context): String =
        "Run N back-to-back benchmark passes on one device: silicon cool gate between passes, traces copied aside per " +
            "pass, battery and silicon temperatures logged, one retry for the install-timeout signature, run-metadata.json for ingest."

    override fun run() {
        val rc = FleetCampaign(
            serial = serial,
            dirMatch = dirMatch,
            campDir = out.toAbsolutePath(),
            passes = passes,
            method = method,
            iterations = iterations,
            gapAfterPass = gapAfterPass,
            repo = repo ?: RepoRoot.locate(),
            dryRun = dryRun,
            verifyCohort = !noVerifyCohort,
        ).run()
        if (rc != 0) throw ProgramResult(rc)
    }
}
