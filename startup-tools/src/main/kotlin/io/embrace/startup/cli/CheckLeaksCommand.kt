package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.check.LeakCheck
import io.embrace.startup.core.repo.RepoRoot
import java.nio.file.Path

/**
 * `check-leaks` - the former `check_leaks.py` in the production repo: sweep the skills and the tooling
 * for one author's personal setup, so what ships describes the work rather than the machine it ran on.
 * Exits 1 when anything is found, so it can gate a commit.
 */
class CheckLeaksCommand : CliktCommand(name = "check-leaks") {

    private val extra by argument("path", help = "extra files or directories to scan as source, whatever their extension")
        .path(mustExist = true).multiple()
    private val allow by option("--allow", metavar = "REGEX", help = "suppress any hit whose matched text matches REGEX (repeatable)")
        .multiple()
    private val selfTest by option("--self-test", help = "check every pattern against its own samples instead of scanning")
        .flag()
    private val repo by option("--repo", help = "repo root; defaults to git rev-parse --show-toplevel").path()

    override fun help(context: Context): String =
        "Scan the skills and the tooling for a personal-setup leak: home paths, addresses, device serials, " +
            "private artifact links, scratch paths and run-specific citations."

    override fun run() {
        if (selfTest) {
            val failures = LeakCheck.selfTest()
            failures.forEach { echo("self-test FAIL: $it", err = true) }
            echo("${LeakCheck.PATTERNS.size} pattern(s) checked, ${failures.size} problem(s)")
            if (failures.isNotEmpty()) throw ProgramResult(1)
            return
        }

        val root: Path = repo ?: RepoRoot.locate()
        val tokens = LeakCheck.loadLocalTokens(root)
        val report = LeakCheck.scan(root, extra, allow.map { Regex(it) }, tokens)

        if (report.localTokenFile == null) {
            echo("no local token list (.leakcheck-local or \$LEAKCHECK_LOCAL): serials and app ids were not checked by name")
        }
        report.findings.forEach { echo(it.toString()) }
        echo("${report.filesChecked} files checked, ${report.findings.size} problem(s)")
        if (report.findings.isNotEmpty()) throw ProgramResult(1)
    }
}
