package io.embrace.analysis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import io.embrace.analysis.cli.AnalyzeCommand
import io.embrace.analysis.cli.ArtifactSyncCommand
import io.embrace.analysis.cli.CellRunnerCommand
import io.embrace.analysis.cli.CheckLocalRefsCommand
import io.embrace.analysis.cli.CohortsCommand
import io.embrace.analysis.cli.CompatPatchCommand
import io.embrace.analysis.cli.CrossDeviceSectionsCommand
import io.embrace.analysis.cli.FactorsReportCommand
import io.embrace.analysis.cli.FleetCampaignCommand
import io.embrace.analysis.cli.HypothesisTestsCommand
import io.embrace.analysis.cli.IngestCommand
import io.embrace.analysis.cli.MatrixPlanCommand
import io.embrace.analysis.cli.MatrixReportCommand
import io.embrace.analysis.cli.MaximsCommand
import io.embrace.analysis.cli.OutlierFactorsCommand
import io.embrace.analysis.cli.ProbeCommand
import io.embrace.analysis.cli.RecordsCommand
import io.embrace.analysis.cli.ReferenceSetCommand
import io.embrace.analysis.cli.ReproducibilityCommand
import io.embrace.analysis.cli.ServeTraceCommand
import io.embrace.analysis.cli.SubmitCommand
import io.embrace.analysis.cli.TraceHealthCommand
import io.embrace.analysis.cli.TrendCommand
import io.embrace.analysis.cli.VarianceCommand
import io.embrace.analysis.cli.VerifyArmsCommand

/**
 * The toolchain's complete surface, in registration order; each command's behaviour is held to the
 * frozen goldens by its own parity gate.
 */
fun allCommands(): List<CliktCommand> = listOf(
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
    RecordsCommand(),
    CheckLocalRefsCommand(),
    // Every former script is now a subcommand.
)

/** Entry point. */
fun main(args: Array<String>) = StartupTools()
    .subcommands(allCommands())
    .main(args)

/**
 * Bumped by hand when behaviour changes in a way a stored record could depend on; the store's
 * `source_skill` provenance will carry it so a number can always be traced to the tool that made it.
 */
internal const val VERSION: String = "0.1.0-phase0"
