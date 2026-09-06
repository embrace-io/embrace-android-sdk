package io.embrace.startup.core.repo

import io.embrace.startup.core.proc.Processes
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Locate the SDK repo root without any hardcoded path, via the standard three-step fallback:
 * `git rev-parse --show-toplevel` (correct inside worktrees and submodules), else the
 * nearest ancestor containing `.git`, else the working directory, so a tool still runs outside a
 * checkout.
 */
object RepoRoot {

    fun locate(from: Path = Path.of("").toAbsolutePath()): Path {
        gitTopLevel(from)?.let { return it }
        var dir: Path? = from.toAbsolutePath()
        while (dir != null) {
            if (Files.exists(dir.resolve(".git"))) return dir
            dir = dir.parent
        }
        return Path.of("").toAbsolutePath()
    }

    /**
     * The scratch directory for raw outputs: `<repo>/claude-output` (project-local, gitignored). Traces and
     * whole campaign directories land here; nothing that must outlive the machine belongs here.
     */
    fun claudeOutput(repo: Path = locate()): Path = repo.resolve("claude-output")

    /**
     * The committed records root, [RECORDS_REL] under the repo: everything the startup tooling learns
     * and must keep - the longitudinal store and reference set, the maxims ledger and its pages, the
     * living-document sources with their publish manifest, each campaign's per-pass datasets, and the
     * per-run analysis summaries. Raw traces are the one thing that stays out (hundreds of megabytes per
     * pass); the datasets and summaries derived from them are the durable record.
     */
    fun records(repo: Path = locate()): Path = repo.resolve(RECORDS_REL)

    const val RECORDS_REL: String = ".claude/skills/_shared/records"

    private fun gitTopLevel(from: Path): Path? = runCatching {
        val result = Processes.run(
            listOf("git", "-C", from.toString(), "rev-parse", "--show-toplevel"),
            timeout = Duration.ofSeconds(GIT_TIMEOUT_S),
        )
        val out = result.stdout.trim()
        if (result.exitCode == 0 && out.isNotEmpty()) Path.of(out) else null
    }.getOrNull()

    private const val GIT_TIMEOUT_S = 60L
}
