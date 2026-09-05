package io.embrace.startup

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.embrace.startup.cli.AnalyzeCommand
import io.embrace.startup.cli.ArtifactSyncCommand
import io.embrace.startup.cli.CellRunnerCommand
import io.embrace.startup.cli.CohortsCommand
import io.embrace.startup.cli.CompatPatchCommand
import io.embrace.startup.cli.CrossDeviceSectionsCommand
import io.embrace.startup.cli.FactorsReportCommand
import io.embrace.startup.cli.FleetCampaignCommand
import io.embrace.startup.cli.HypothesisTestsCommand
import io.embrace.startup.cli.IngestCommand
import io.embrace.startup.cli.MatrixPlanCommand
import io.embrace.startup.cli.MatrixReportCommand
import io.embrace.startup.cli.MaximsCommand
import io.embrace.startup.cli.OutlierFactorsCommand
import io.embrace.startup.cli.ProbeCommand
import io.embrace.startup.cli.ReferenceSetCommand
import io.embrace.startup.cli.ReproducibilityCommand
import io.embrace.startup.cli.ServeTraceCommand
import io.embrace.startup.cli.SubmitCommand
import io.embrace.startup.cli.TraceHealthCommand
import io.embrace.startup.cli.TrendCommand
import io.embrace.startup.cli.VarianceCommand
import io.embrace.startup.cli.VerifyArmsCommand

/**
 * Entry point. The subcommand list is the complete port surface, extended phase by phase as each
 * former Python script passes its parity gate against the goldens.
 */
fun main(args: Array<String>) = StartupTools()
    .subcommands(
        AnalyzeCommand(),
        VarianceCommand(),
        OutlierFactorsCommand(),
        HypothesisTestsCommand(),
        FactorsReportCommand(),
        CrossDeviceSectionsCommand(),
        TraceHealthCommand(),
        TrendCommand(),
        ReproducibilityCommand(),
        MatrixPlanCommand(),
        ServeTraceCommand(),
        IngestCommand(),
        ReferenceSetCommand(),
        SubmitCommand(),
        ProbeCommand(),
        VerifyArmsCommand(),
        FleetCampaignCommand(),
        CohortsCommand(),
        CompatPatchCommand(),
        CellRunnerCommand(),
        MatrixReportCommand(),
        ArtifactSyncCommand(),
        MaximsCommand(),
        // Every former script is now a subcommand.
    )
    .main(args)

/**
 * Bumped by hand when behaviour changes in a way a stored record could depend on; the store's
 * `source_skill` provenance will carry it so a number can always be traced to the tool that made it.
 */
internal const val VERSION: String = "0.1.0-phase0"
