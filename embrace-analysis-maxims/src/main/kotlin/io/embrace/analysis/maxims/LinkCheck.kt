package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.records.store.ArchiveHygiene
import io.embrace.analysis.records.store.DeviceSerials
import java.nio.file.Files
import java.nio.file.Path

/**
 * The provenance chain, checked both ways: every statement in the records root traces to evidence, and
 * every piece of evidence backs a statement.
 *
 * - A campaign archive must be scored (listed in `ledger-runs.json`) or cited in FINDINGS.md, else it is
 *   an ORPHAN: data nobody has drawn a conclusion from, which either wants scoring or wants deleting.
 * - Every run `ledger-runs.json` names must have its archive, else the ledger is not reproducible.
 * - Every archive FINDINGS.md cites (a backticked run or experiment id, or a `campaigns/...`,
 *   `experiments/...` path) must exist, else the citation DANGLES.
 * - An experiment archive must be cited by FINDINGS.md: it is an argument, and an argument nobody has
 *   drawn a conclusion from has no reason to be committed.
 * - An archive may hold only evidence, with the device named by key: a raw member or a serial in a
 *   committed archive is a hygiene failure (`records cull` fixes it).
 * - `campaigns/index.json` must be what `records index` would write now.
 */
object LinkCheck {

    data class Problem(val kind: String, val subject: String, val detail: String) {
        override fun toString(): String = "$kind  $subject  - $detail"
    }

    fun run(records: Path, serials: DeviceSerials.Serials = DeviceSerials.Serials.EMPTY): List<Problem> {
        val problems = ArrayList<Problem>()
        val campaigns = EvidenceIndex.archives(records).map { it.fileName.toString().removeSuffix(".zip") }.toSet()
        val experiments = stems(records.resolve("experiments"))
        val runs = EvidenceIndex.ledgerRuns(records)
        val findings = records.resolve("maxims").resolve("FINDINGS.md").takeIf {
            Files.isRegularFile(it)
        }?.let { Files.readString(it) } ?: ""
        val cited = citations(findings)

        campaigns.filterNot { it in runs || it in cited }.sorted().forEach {
            problems.add(Problem("orphan", "campaigns/$it.zip", "neither scored in ledger-runs.json nor cited in FINDINGS.md"))
        }
        runs.keys.filterNot { it in campaigns }.sorted().forEach {
            problems.add(Problem("dangling", "ledger-runs.json: $it", "no campaigns/$it.zip - the ledger cannot be rebuilt"))
        }
        experiments.filterNot { it in cited }.sorted().forEach {
            problems.add(Problem("orphan", "experiments/$it.zip", "not cited in FINDINGS.md - an argument with no conclusion drawn"))
        }
        cited.filterNot { it in campaigns || it in experiments }.sorted().forEach {
            problems.add(Problem("dangling", "FINDINGS.md: `$it`", "cites an archive that does not exist"))
        }
        EvidenceIndex.archives(records).forEach { archive -> problems.addAll(hygiene(archive)) }
        problems.addAll(indexProblems(records, serials))
        return problems
    }

    /**
     * Archive references in a FINDINGS page: backticked tokens naming a `campaigns/` or `experiments/`
     * path, or ending in `.zip`, reduced to the archive stem. Plain backticked run ids are also accepted
     * when they match an existing archive, which [run] resolves by intersecting with what exists - a
     * token that matches nothing is only a dangling citation when it looks like a path.
     */
    fun citations(findings: String): Set<String> {
        val out = LinkedHashSet<String>()
        BACKTICKED.findAll(findings).forEach { m ->
            val token = m.groupValues[1].trim()
            val stem = when {
                token.startsWith("campaigns/") || token.startsWith("experiments/") ||
                    token.startsWith("records/campaigns/") || token.startsWith("records/experiments/") ->
                    token.substringAfterLast('/').removeSuffix(".zip")
                token.endsWith(".zip") && '/' !in token -> token.removeSuffix(".zip")
                RUN_ID.matches(token) -> token
                else -> null
            }
            stem?.let { out.add(it) }
        }
        return out
    }

    private fun hygiene(archive: Path): List<Problem> {
        val members = Zips.readAll(archive)
        val rel = "campaigns/${archive.fileName}"
        val problems = ArrayList<Problem>()
        members.keys.filterNot { ArchiveHygiene.isEvidence(it) }.forEach {
            problems.add(Problem("raw-member", "$rel!$it", "not evidence; `records cull` moves it to the local root"))
        }
        members.filterKeys { it.substringAfterLast('/') in PROVENANCE }.forEach { (name, blob) ->
            if ("\"serial\"" in blob.toString(Charsets.UTF_8)) {
                problems.add(
                    Problem("serial", "$rel!$name", "provenance names the device by serial; `records cull` replaces it with the key"),
                )
            }
        }
        return problems
    }

    private fun indexProblems(records: Path, serials: DeviceSerials.Serials): List<Problem> {
        val file = EvidenceIndex.file(records)
        if (EvidenceIndex.archives(records).isEmpty()) return emptyList()
        if (!Files.isRegularFile(file)) {
            return listOf(Problem("index-missing", "campaigns/${EvidenceIndex.FILE_NAME}", "run `records index`"))
        }
        val fresh = try {
            EvidenceIndex.render(EvidenceIndex.build(records, serials))
        } catch (e: IllegalArgumentException) {
            return listOf(Problem("unreadable", "campaigns/", "an archive cannot be indexed: ${e.message}"))
        } catch (e: IllegalStateException) {
            return listOf(Problem("unreadable", "campaigns/", "an archive cannot be indexed: ${e.message}"))
        }
        if (Files.readString(file) != fresh) {
            return listOf(Problem("index-stale", "campaigns/${EvidenceIndex.FILE_NAME}", "differs from what `records index` writes now"))
        }
        return emptyList()
    }

    private fun stems(dir: Path): Set<String> {
        if (!Files.isDirectory(dir)) return emptySet()
        return Files.list(dir).use { s ->
            s.filter { it.fileName.toString().endsWith(".zip") }.map { it.fileName.toString().removeSuffix(".zip") }.toList()
        }.toSet()
    }

    private val BACKTICKED = Regex("`([^`\\n]+)`")
    private val PROVENANCE = setOf("run-metadata.json", "cell-state.json")

    /**
     * What a run or experiment id looks like: a slug carrying a `YYYY-MM` or `YYYY-MM-DD` date somewhere
     * (`campaign-2026-08-11`, `2026-08-16-x25-artifacts`), never a bare section or attribute name.
     */
    private val RUN_ID = Regex("[A-Za-z0-9._-]*20\\d\\d-\\d\\d(-\\d\\d)?[A-Za-z0-9._-]*")
}
