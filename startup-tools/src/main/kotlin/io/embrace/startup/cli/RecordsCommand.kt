package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.core.io.Zips
import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.repo.RepoRoot
import io.embrace.startup.store.MaximsRunner
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `records` - housekeeping for the committed records root (see its README). Two subcommands, both of
 * which only ever touch that directory: `pack` folds write-once output into one archive per set, and
 * `rebuild-ledger` re-scores every kept campaign from scratch so the maxims ledger is reproducible
 * rather than accumulated.
 */
class RecordsCommand : CliktCommand(name = "records") {
    init {
        subcommands(Pack(), RebuildLedger())
    }

    override fun help(context: Context): String =
        "Maintain the committed records root: `pack` loose output into its archives, `rebuild-ledger` " +
            "re-score every kept campaign into a fresh maxims ledger."

    override fun run() = Unit

    private class Pack : CliktCommand(name = "pack") {
        private val records by option("--records", help = "records root (default: ${RepoRoot.RECORDS_REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Fold every loose analysis summary into its month's archive, and any dropped directory into one archive."

        override fun run() = echo(pack(records ?: RepoRoot.records()), trailingNewline = false)
    }

    private class RebuildLedger : CliktCommand(name = "rebuild-ledger") {
        private val records by option("--records", help = "records root (default: ${RepoRoot.RECORDS_REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Delete the maxims ledger and re-score every campaign listed in maxims/ledger-runs.json, then render MAXIMS.md."

        override fun run() {
            val text = try {
                rebuildLedger(records ?: RepoRoot.records())
            } catch (e: IllegalArgumentException) {
                echo(e.message, err = true)
                throw ProgramResult(1)
            }
            echo(text, trailingNewline = false)
        }
    }

    companion object {
        /**
         * `pack`: every directory directly under `campaigns/`, `experiments/` and `documents/` becomes a
         * sibling `<name>.zip`, and every loose file in `analyses/` is folded into `analyses/<YYYY-MM>.zip`
         * by the date in its name (its last-modified month when the name carries none). Idempotent.
         *
         * Runs deliberately write loose files and this folds them in later: an archive is rewritten whole,
         * and git keeps a full copy of each rewrite, so folding on every run would cost far more history
         * than the few kilobytes of text a run actually produces.
         */
        fun pack(records: Path): String {
            val out = StringBuilder()
            DIRECTORY_SETS.forEach { set ->
                val parent = records.resolve(set)
                if (!Files.isDirectory(parent)) return@forEach
                childDirectories(parent).forEach { dir ->
                    val dest = parent.resolve("${dir.fileName}.zip")
                    val count = Files.walk(dir).use { walk -> walk.filter { Files.isRegularFile(it) }.count() }
                    Zips.packTree(dir, dest)
                    deleteTree(dir)
                    out.append("$set/${dest.fileName}: $count files\n")
                }
            }
            val analyses = records.resolve(ANALYSES)
            if (Files.isDirectory(analyses)) {
                looseFiles(analyses).groupBy { monthOf(it) }.toSortedMap().forEach { (month, files) ->
                    val total = Zips.merge(analyses.resolve("$month.zip"), files)
                    files.forEach { Files.delete(it) }
                    out.append("$ANALYSES/$month.zip: +${files.size} files ($total total)\n")
                }
            }
            return if (out.isEmpty()) "records: nothing to pack\n" else out.toString()
        }

        /**
         * `rebuild-ledger`: the ledger is derived state, and this is its definition. Deletes it, scores every
         * campaign archive named in `maxims/ledger-runs.json` with that entry's cell overrides, and renders
         * MAXIMS.md. Scoring happens in-process, so the whole rebuild is one command rather than N.
         */
        fun rebuildLedger(records: Path): String {
            val runsFile = records.resolve(MAXIMS).resolve(RUNS_FILE)
            require(Files.exists(runsFile)) { "no $RUNS_FILE under ${records.resolve(MAXIMS)} - it lists the campaigns to score" }
            val runs = PyJson.arr(StartupJson.parseToJsonElement(Files.readString(runsFile)).jsonObject, "runs")
                ?.map { it.jsonObject }
                .orEmpty()
            require(runs.isNotEmpty()) { "$RUNS_FILE lists no runs" }

            val ledger = records.resolve(MAXIMS).resolve("ledger.json")
            Files.deleteIfExists(ledger)
            val reference = records.resolve("longitudinal").resolve("reference-set.json").takeIf { Files.exists(it) }
            val out = StringBuilder()
            runs.forEach { run ->
                val id = requireNotNull(PyJson.strOrNull(run, "run")) { "a runs entry has no \"run\" id" }
                val archive = records.resolve(CAMPAIGNS).resolve("$id.zip")
                require(Files.exists(archive)) { "no campaign archive $archive for run $id" }
                out.append(
                    MaximsRunner.score(
                        campaign = archive,
                        referenceSet = reference,
                        deviceKey = PyJson.strOrNull(run, "device"),
                        sdkVersion = PyJson.strOrNull(run, "sdk"),
                        arm = PyJson.strOrNull(run, "arm"),
                        ledger = ledger.toString(),
                        runId = id,
                        now = null,
                        recordsRoot = records,
                    ),
                )
            }
            out.append(MaximsRunner.render(ledger.toString(), records.resolve(MAXIMS).resolve("MAXIMS.md").toString(), null))
            return out.toString()
        }

        private fun childDirectories(parent: Path): List<Path> =
            Files.list(parent).use { it.filter { p -> Files.isDirectory(p) }.sorted().toList() }

        private fun looseFiles(dir: Path): List<Path> =
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".zip") }.sorted().toList()
            }

        private fun deleteTree(dir: Path) {
            Files.walk(dir).use { walk -> walk.sorted(Comparator.reverseOrder()).toList() }.forEach { Files.delete(it) }
        }

        /** The `YYYY-MM` in the file's name, else the month it was last written. */
        private fun monthOf(file: Path): String {
            MONTH_IN_NAME.find(file.fileName.toString())?.let { return it.value }
            return MONTH.format(Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.systemDefault()))
        }

        private const val ANALYSES = "analyses"
        private const val CAMPAIGNS = "campaigns"
        private const val MAXIMS = "maxims"
        private const val RUNS_FILE = "ledger-runs.json"
        private val DIRECTORY_SETS = listOf(CAMPAIGNS, "experiments", "documents")
        private val MONTH_IN_NAME = Regex("20\\d\\d-\\d\\d")
        private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
