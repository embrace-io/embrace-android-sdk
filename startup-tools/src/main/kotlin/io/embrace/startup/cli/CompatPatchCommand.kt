package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.campaign.CompatPatch
import io.embrace.startup.core.repo.RepoRoot

/** `compat-patch`: per-version app-side compatibility patches plus the version pin. */
class CompatPatchCommand : CliktCommand(name = "compat-patch") {

    private val version by option("--version", help = "SDK version to pin (or `local` for the working tree's version)")
    private val apply by option("--apply").flag()
    private val verify by option("--verify", help = "with --apply: compile-check the patched tree").flag()
    private val revertAll by option("--revert-all", help = "restore every journaled file").flag()
    private val status by option("--status", help = "say whether this checkout is currently patched, and by whom").flag()
    private val repo by option("--repo").path()

    override fun help(context: Context): String =
        "Apply or revert the per-version ExampleApp compatibility patches (7.x swazzler plugin + fcm dep; 6.x plugin-less) " +
            "and set the catalog pin; every patched file is journaled so --revert-all restores the tree after a dead run."

    override fun run() {
        val patch = CompatPatch(repo ?: RepoRoot.locate())
        if (status) {
            patch.status().forEach { echo(it) }
            return
        }
        if (revertAll) {
            patch.revertAll().forEach { echo(it) }
            return
        }
        val v = version
        if (v == null || !apply) {
            echo("use --version X --apply, or --revert-all, or --status", err = true)
            throw ProgramResult(1)
        }
        try {
            patch.apply(v).forEach { echo(it) }
        } catch (e: CompatPatch.PatchError) {
            echo(e.message, err = true)
            throw ProgramResult(1)
        }
        if (verify) {
            val (ok, lines) = patch.verifyBuild()
            lines.forEach { echo(it) }
            if (!ok) throw ProgramResult(1)
        }
    }
}
