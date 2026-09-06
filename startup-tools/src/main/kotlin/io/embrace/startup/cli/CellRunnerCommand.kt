package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.campaign.CellRunner
import io.embrace.startup.core.repo.RepoRoot

/** `cell-runner`: run ONE matrix cell with every invariant machine-checked first. */
class CellRunnerCommand : CliktCommand(name = "cell-runner") {

    private val cells by option("--cells", help = "the cell list written by `matrix-plan --emit`").path(mustExist = true).required()
    private val cell by option("--cell", help = "cell id, e.g. mid|9.2.0|reference").required()
    private val out by option("--out", help = "run directory (default: <repo>/claude-output/vfm-<run_id>)").path()
    private val checkOnly by option("--check-only", help = "invariants and provenance only, no passes").flag()
    private val repo by option("--repo").path()

    override fun help(context: Context): String =
        "Run one matrix cell: set the factor state, machine-check every invariant (host quiet, resolved SDK, temperature, " +
            "compile state), run the passes through fleet-campaign, check the window instrument, record cell-state.json. " +
            "A failed invariant is a STOP."

    override fun run() {
        try {
            CellRunner(repo ?: RepoRoot.locate()).run(cells, cell, out, checkOnly)
        } catch (e: CellRunner.Abort) {
            echo(e.message, err = true)
            throw ProgramResult(1)
        }
    }
}
