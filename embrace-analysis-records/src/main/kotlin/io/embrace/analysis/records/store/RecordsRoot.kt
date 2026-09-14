package io.embrace.analysis.records.store

import io.embrace.analysis.common.repo.RepoRoot
import java.nio.file.Path

/**
 * Where this repository keeps the toolchain's durable record, where it keeps what stays on one machine,
 * and where it keeps its scratch. This is the one place that layout is written down: the modules below
 * this one know how to find a repository root ([RepoRoot]), not what is inside it.
 *
 * Three roots, by what happens to a file if the machine is lost:
 * - the COMMITTED root ([dir]) holds what a statement is computed from and the statements themselves;
 * - the LOCAL root ([local]) holds what is bulky, identifying, or re-derivable from the committed set, and
 *   is gitignored but never treated as scratch;
 * - the SCRATCH root ([claudeOutput]) holds traces and running campaigns and may be purged at any time.
 */
object RecordsRoot {

    /**
     * The committed records root under the repo: everything a statement rests on and must outlive the
     * machine - the longitudinal store and reference set (without serials), the maxims ledger and its
     * pages, the living-document sources with their publish manifest, each campaign's evidence archive
     * (per-pass datasets, harness output, log, provenance naming the device by key), and the per-run
     * analysis summaries. Raw traces, device output and anything identifying stay out.
     */
    const val REL: String = ".claude/skills/_shared/records"

    /**
     * The machine-local root beside the committed one, gitignored. `data/` is the only copy of anything
     * under it - the device-serial map, the members culled from evidence archives, run directories - and
     * is never deleted by the tooling; `pages/` is rendered from data and safe to delete at any time.
     * It sits beside `records` rather than under the scratch directory so that a scratch clean-up
     * cannot take a machine's history with it.
     */
    const val LOCAL_REL: String = ".claude/skills/_shared/local"

    fun dir(repo: Path = RepoRoot.locate()): Path = repo.resolve(REL)

    fun local(repo: Path = RepoRoot.locate()): Path = repo.resolve(LOCAL_REL)

    fun localData(repo: Path = RepoRoot.locate()): Path = local(repo).resolve("data")

    fun localPages(repo: Path = RepoRoot.locate()): Path = local(repo).resolve("pages")

    /** Where campaign and cell runs land by default: `<local>/data/runs/<run-id>/`, so a run outlives a scratch purge. */
    fun localRuns(repo: Path = RepoRoot.locate()): Path = localData(repo).resolve("runs")

    /**
     * The scratch directory for raw outputs: `<repo>/claude-output`, project-local and gitignored. Traces
     * and one-off comparisons land here; nothing that must outlive the machine belongs here.
     */
    fun claudeOutput(repo: Path = RepoRoot.locate()): Path = repo.resolve(SCRATCH_REL)

    private const val SCRATCH_REL = "claude-output"
}
