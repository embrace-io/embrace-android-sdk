package io.embrace.analysis.common.repo

import io.embrace.analysis.common.proc.Processes
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Locate the SDK repo root without any hardcoded path, via the standard three-step fallback:
 * `git rev-parse --show-toplevel` (correct inside worktrees and submodules), else the
 * nearest ancestor containing `.git`, else the working directory, so a tool still runs outside a
 * checkout. Only the root: what a particular repository keeps where is that repository's business,
 * written down in the module that owns those paths, not here.
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
