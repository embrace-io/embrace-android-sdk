package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.campaign.Reproduce
import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.maxims.MaximsRunner
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.EvidenceClass

/**
 * `reproduce`: the commands that would produce a recorded run's traces again, from its provenance. Exit 1
 * when the run is irreproducible (a dirty tree or no recorded commit), so a script can tell the two apart.
 */
class ReproduceCommand : CliktCommand(name = "reproduce") {

    private val campaign by argument("campaign", help = "a campaign directory, or a campaigns/<run-id>.zip from the records root")
        .path(mustExist = true)
    private val repo by option("--repo").path()

    override fun help(context: Context): String =
        "Print the checkout, pin and campaign commands that re-create a recorded run from its provenance, with its evidence class."

    override fun run() {
        val dir = MaximsRunner.materialize(campaign)
        val runId = campaign.fileName.toString().removeSuffix(".zip")
        val plan = Reproduce.plan(dir, runId, DeviceSerials.load(DeviceSerials.file()), repo ?: RepoRoot.locate())
        if (plan == null) {
            echo("no provenance (run-metadata.json or cell-state.json) under $campaign", err = true)
            throw ProgramResult(1)
        }
        echo(Reproduce.render(plan), trailingNewline = false)
        if (plan.evidence.klass == EvidenceClass.IRREPRODUCIBLE) throw ProgramResult(1)
    }
}
