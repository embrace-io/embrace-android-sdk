package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.localrefs.LocalRefsCheck
import java.nio.file.Path

/**
 * `check-local-refs`: sweep the skills and the tooling for references to one machine or one author, so
 * what ships describes the work rather than the machine it ran on. Exits 1 when anything is found, so it
 * can gate a commit.
 *
 * Ported from `_shared/check_leaks.py` in the production `sdk-startup` repo, which is a live tool
 * there, not one of the scripts this module replaced - see [io.embrace.analysis.localrefs.LocalRefsCheck]
 * for what this port kept and where it deliberately differs. The checker itself knows no repository
 * layout; this command supplies this repository's as [RepoLocalRefs.ROOTS].
 */
class CheckLocalRefsCommand : CliktCommand(name = "check-local-refs") {

    private val extra by argument("path", help = "extra files or directories to scan as source, whatever their extension")
        .path(mustExist = true).multiple()
    private val allow by option("--allow", metavar = "REGEX", help = "suppress any hit whose matched text matches REGEX (repeatable)")
        .multiple()
    private val selfTest by option("--self-test", help = "check every pattern against its own samples instead of scanning")
        .flag()
    private val repo by option("--repo", help = "repo root; defaults to git rev-parse --show-toplevel").path()

    override fun help(context: Context): String =
        "Scan the skills and the tooling for machine-specific references: home paths, addresses, device serials, " +
            "private artifact links, scratch paths and run-specific citations."

    override fun run() {
        if (selfTest) {
            val failures = LocalRefsCheck.selfTest()
            failures.forEach { echo("self-test FAIL: $it", err = true) }
            echo("${LocalRefsCheck.PATTERNS.size} pattern(s) checked, ${failures.size} problem(s)")
            if (failures.isNotEmpty()) throw ProgramResult(1)
            return
        }

        val root: Path = repo ?: RepoRoot.locate()
        val tokens = LocalRefsCheck.loadLocalTokens(root)
        val report = LocalRefsCheck.scan(root, extra, allow.map { Regex(it) }, tokens, RepoLocalRefs.ROOTS)

        if (report.localTokenFile == null) {
            echo("no local token list (.local-refs-tokens or \$LOCAL_REFS_TOKENS): serials and app ids were not checked by name")
        }
        report.findings.forEach { echo(it.toString()) }
        echo("${report.filesChecked} files checked, ${report.findings.size} problem(s)")
        if (report.findings.isNotEmpty()) throw ProgramResult(1)
    }
}
