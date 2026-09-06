package io.embrace.startup.core.repo

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Locate the SDK repo root without any hardcoded path - the same three-step fallback every Python
 * script carried: `git rev-parse --show-toplevel` (correct inside worktrees and submodules), else the
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

    /** The default analysis output directory: `<repo>/claude-output` (project-local, gitignored). */
    fun claudeOutput(repo: Path = locate()): Path = repo.resolve("claude-output")

    private fun gitTopLevel(from: Path): Path? = runCatching {
        val process = ProcessBuilder("git", "-C", from.toString(), "rev-parse", "--show-toplevel")
            .redirectErrorStream(false)
            .start()
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.errorStream.bufferedReader().use { it.readText() }
        if (process.waitFor(GIT_TIMEOUT_S, TimeUnit.SECONDS) && process.exitValue() == 0 && out.isNotEmpty()) {
            Path.of(out)
        } else {
            null
        }
    }.getOrNull()

    private const val GIT_TIMEOUT_S = 60L
}
