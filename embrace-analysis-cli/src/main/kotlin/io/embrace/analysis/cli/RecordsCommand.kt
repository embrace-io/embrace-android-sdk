package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.maxims.EvidenceIndex
import io.embrace.analysis.maxims.LinkCheck
import io.embrace.analysis.maxims.MaximsRunner
import io.embrace.analysis.records.store.ArchiveHygiene
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.RecordsRoot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `records` - housekeeping for the committed records root (see its README), and the checks that keep
 * it honest. Every subcommand touches only that root and the machine-local root beside it:
 * `pack` folds write-once output into one archive per set; `cull` moves raw members out of the evidence
 * archives and replaces serials with device keys; `index` regenerates `campaigns/index.json`;
 * `check-links` verifies that every statement traces to evidence and every archive backs a statement;
 * `rebuild-ledger` re-scores every kept campaign from scratch so the maxims ledger is reproducible
 * rather than accumulated.
 */
class RecordsCommand : CliktCommand(name = "records") {
    init {
        subcommands(Pack(), Cull(), Index(), CheckLinks(), RebuildLedger())
    }

    override fun help(context: Context): String =
        "Maintain the committed records root: `pack` loose output into its archives, `cull` raw members and serials " +
            "out of them, `index` the evidence archives, `check-links` the provenance chain, `rebuild-ledger` re-score " +
            "every kept campaign into a fresh maxims ledger."

    override fun run() = Unit

    private class Pack : CliktCommand(name = "pack") {
        private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Fold every loose analysis summary into its month's archive, and any dropped directory into one archive."

        override fun run() = echo(pack(records ?: RecordsRoot.dir()), trailingNewline = false)
    }

    private class Cull : CliktCommand(name = "cull") {
        private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
            .path(mustExist = true, canBeFile = false)
        private val local by option("--local", help = "machine-local root (default: ${RecordsRoot.LOCAL_REL} under the repo)")
            .path(canBeFile = false)
        private val dryRun by option("--dry-run", help = "report what would move or change; write nothing").flag()

        override fun help(context: Context): String =
            "Move raw members (logcat captures, rendered reports, build logs) out of the campaign archives into the local " +
                "root, replace serials with device keys in provenance, strip serials from experiment archives and the " +
                "reference set. Idempotent."

        override fun run() = echo(cull(records ?: RecordsRoot.dir(), local ?: RecordsRoot.local(), dryRun), trailingNewline = false)
    }

    private class Index : CliktCommand(name = "index") {
        private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Regenerate campaigns/index.json: one entry per evidence archive with its cell, parameters, summary, " +
                "evidence class and replicate number."

        override fun run() = echo(EvidenceIndex.write(records ?: RecordsRoot.dir(), localSerials()), trailingNewline = false)
    }

    private class CheckLinks : CliktCommand(name = "check-links") {
        private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Verify the provenance chain: every archive is scored or cited, every citation and ledger run has its archive, " +
                "archives hold only evidence, and the index is current. Exit 1 on any problem."

        override fun run() {
            val problems = LinkCheck.run(records ?: RecordsRoot.dir(), localSerials())
            problems.forEach { echo(it.toString()) }
            echo("records: ${problems.size} problem(s)")
            if (problems.isNotEmpty()) throw ProgramResult(1)
        }
    }

    private class RebuildLedger : CliktCommand(name = "rebuild-ledger") {
        private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
            .path(mustExist = true, canBeFile = false)

        override fun help(context: Context): String =
            "Delete the maxims ledger and re-score every campaign listed in maxims/ledger-runs.json, then render " +
                "MAXIMS.md and the index."

        override fun run() {
            val text = try {
                rebuildLedger(records ?: RecordsRoot.dir(), localSerials())
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
         * `cull`: bring the committed root to the evidence-only rule. Campaign archives lose their raw
         * members to `<local>/data/campaigns/<run-id>/` and name the device by key; experiment archives lose
         * their serials; the reference set's serials move to the local device-serial map. The serial map is
         * read first and written last, so a run recorded before the split can still be mapped, and a
         * `--dry-run` writes nothing at all.
         */
        fun cull(records: Path, local: Path, dryRun: Boolean = false): String {
            val out = StringBuilder()
            val serialFile = local.resolve("data").resolve(DeviceSerials.FILE_NAME)
            val refFile = records.resolve("longitudinal").resolve("reference-set.json")
            val refDoc = refFile.takeIf {
                Files.isRegularFile(it)
            }?.let { StartupJson.parseToJsonElement(Files.readString(it)).jsonObject }
            val serials = DeviceSerials.merge(DeviceSerials.load(serialFile), refDoc)
            if (serials.isEmpty()) {
                out.append(
                    "records: no device serials known (no ${DeviceSerials.FILE_NAME} and none in the reference set) - " +
                        "serials cannot be replaced by keys\n",
                )
            }
            cullCampaigns(records, local.resolve("data").resolve(CAMPAIGNS), serials, dryRun, out)
            scrubExperiments(records, serials, dryRun, out)
            if (refDoc != null) {
                val (stripped, fromRef) = DeviceSerials.split(refDoc)
                if (!fromRef.isEmpty()) {
                    if (!dryRun) {
                        Files.writeString(refFile, PRETTY.encodeToString(JsonObject.serializer(), stripped))
                        DeviceSerials.save(serials, serialFile)
                    }
                    out.append("longitudinal/reference-set.json: ${fromRef.byKey.size} serial(s) moved to $serialFile\n")
                }
            }
            if (out.isEmpty()) return "records: nothing to cull\n"
            return (if (dryRun) "DRY RUN - nothing written\n" else "") + out.toString()
        }

        /**
         * `rebuild-ledger`: the ledger is derived state, and this is its definition. Deletes it, scores every
         * campaign archive named in `maxims/ledger-runs.json` with that entry's cell overrides, renders
         * MAXIMS.md, and regenerates the evidence index. Scoring happens in-process, so the whole rebuild is
         * one command rather than N.
         */
        fun rebuildLedger(records: Path, serials: DeviceSerials.Serials = DeviceSerials.Serials.EMPTY): String {
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
                        serials = serials,
                    ),
                )
            }
            out.append(MaximsRunner.render(ledger.toString(), records.resolve(MAXIMS).resolve("MAXIMS.md").toString(), null))
            out.append(EvidenceIndex.write(records, serials))
            return out.toString()
        }

        /** Every campaign archive through [ArchiveHygiene.cull], one report line per archive that changed. */
        private fun cullCampaigns(
            records: Path,
            localCampaigns: Path,
            serials: DeviceSerials.Serials,
            dryRun: Boolean,
            out: StringBuilder,
        ) {
            EvidenceIndex.archives(records).forEach { archive ->
                val report = if (dryRun) {
                    dryReport(archive) { copy, scratch -> ArchiveHygiene.cull(copy, scratch, serials) }
                } else {
                    ArchiveHygiene.cull(archive, localCampaigns, serials)
                }
                if (!report.changed) return@forEach
                val rawDir = localCampaigns.resolve(archive.fileName.toString().removeSuffix(".zip"))
                out.append("$CAMPAIGNS/${archive.fileName}: ${report.culled.size} member(s) to $rawDir, ${report.scrubbed.size} scrubbed")
                if (report.unresolved.isNotEmpty()) {
                    out.append(" (UNRESOLVED serial in ${report.unresolved.joinToString()})")
                }
                out.append("\n")
            }
        }

        /** Every experiment archive through [ArchiveHygiene.scrub]: serials only, nothing culled. */
        private fun scrubExperiments(
            records: Path,
            serials: DeviceSerials.Serials,
            dryRun: Boolean,
            out: StringBuilder,
        ) {
            val experiments = records.resolve("experiments")
            if (!Files.isDirectory(experiments)) return
            val archives = Files.list(experiments).use { s ->
                s.filter { it.fileName.toString().endsWith(".zip") }.sorted().toList()
            }
            archives.forEach { archive ->
                val report = if (dryRun) {
                    dryReport(archive) { copy, _ -> ArchiveHygiene.scrub(copy, serials) }
                } else {
                    ArchiveHygiene.scrub(archive, serials)
                }
                if (report.scrubbed.isNotEmpty()) {
                    out.append("experiments/${archive.fileName}: ${report.scrubbed.size} member(s) scrubbed of serials\n")
                }
            }
        }

        private fun localSerials(): DeviceSerials.Serials = DeviceSerials.load(DeviceSerials.file())

        /** What [operation] would report for [archive], computed on a copy in a scratch directory so the archive is untouched. */
        private fun dryReport(
            archive: Path,
            operation: (copy: Path, scratchLocal: Path) -> ArchiveHygiene.Report,
        ): ArchiveHygiene.Report {
            val scratch = Files.createTempDirectory("cull-dry-")
            val copy = scratch.resolve(archive.fileName.toString())
            Files.copy(archive, copy)
            return operation(copy, scratch.resolve("local")).copy(archive = archive)
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

        /** One-space indent, the form the reference set is kept in. */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val PRETTY = kotlinx.serialization.json.Json(StartupJson) {
            prettyPrint = true
            prettyPrintIndent = " "
        }
    }
}
