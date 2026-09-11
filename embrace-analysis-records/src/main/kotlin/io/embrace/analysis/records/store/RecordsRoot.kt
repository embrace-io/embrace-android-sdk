package io.embrace.analysis.records.store

import io.embrace.analysis.common.repo.RepoRoot
import java.nio.file.Path

/**
 * Where this repository keeps the toolchain's durable record, and where it keeps its scratch. This is
 * the one place that layout is written down: the modules below this one know how to find a repository
 * root ([RepoRoot]), not what is inside it.
 */
object RecordsRoot {

    /**
     * The committed records root under the repo: everything the startup tooling learns and must keep -
     * the longitudinal store and reference set, the maxims ledger and its pages, the living-document
     * sources with their publish manifest, each campaign's per-pass datasets, and the per-run analysis
     * summaries. Raw traces are the one thing that stays out (hundreds of megabytes per pass); the
     * datasets and summaries derived from them are the durable record.
     */
    const val REL: String = ".claude/skills/_shared/records"

    fun dir(repo: Path = RepoRoot.locate()): Path = repo.resolve(REL)

    /**
     * The scratch directory for raw outputs: `<repo>/claude-output`, project-local and gitignored. Traces
     * and whole campaign directories land here while they run; nothing that must outlive the machine
     * belongs here.
     */
    fun claudeOutput(repo: Path = RepoRoot.locate()): Path = repo.resolve(SCRATCH_REL)

    private const val SCRATCH_REL = "claude-output"
}
