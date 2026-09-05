package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.analysis.Maxims
import io.embrace.startup.analysis.MaximsDoc
import io.embrace.startup.core.io.Zips
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.repo.RepoRoot
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.store.MaximsLedger
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * `maxims` - the former `_shared/maxims.py`: score a campaign directory against every maxim and record
 * the verdicts in the ledger; render MAXIMS.md from the definitions and the ledger. The default ledger
 * and document live in the committed records root (`maxims/` under [RepoRoot.records]), shared with the
 * Python, so both toolchains read and write the same file. Recording a run also packs its per-pass
 * datasets and log into `campaigns/<run-id>.zip` in the same root: the traces they were derived from are
 * wiped by the next benchmark, so the datasets are the durable evidence behind the ledger, and an archive
 * that is written once and never edited is one compressed file rather than a directory to version.
 */
class MaximsCommand : CliktCommand(name = "maxims") {
    init {
        subcommands(Score(), Render())
    }

    override fun help(context: Context): String =
        "The bench's beliefs about SDK init, checked on every campaign and accumulated: `score` a campaign directory " +
            "against every maxim into the ledger, `render` MAXIMS.md from the ledger."

    override fun run() = Unit

    private class Score : CliktCommand(name = "score") {
        private val campaignDir by argument("campaign", help = "a campaign directory, or a campaigns/<run-id>.zip from the records root")
            .path(mustExist = true)
        private val referenceSet by option("--reference-set", help = "reference-set.json, to map the run's serial to a device_key")
            .path(mustExist = true)
        private val deviceKey by option("--device-key", help = "override when provenance cannot identify the device")
        private val sdkVersion by option("--sdk-version", help = "override when provenance does not record the SDK version")
        private val arm by option("--arm", help = "override the arm label (default: the provenance's compile level, else 'default')")
        private val ledger by option(
            "--ledger",
            help = "ledger JSON to update (default: $DEFAULT_LEDGER_REL under the repo); 'none' for a rerun that must not count twice",
        )
        private val runId by option("--run-id", help = "defaults to the campaign directory name")
        private val now by option("--now", help = "timestamp to stamp (tests)")
        private val records by option(
            "--records",
            help = "records root whose campaigns/<run-id>.zip keeps the run's datasets and log once it is recorded " +
                "(default: ${RepoRoot.RECORDS_REL} under the repo); 'none' to keep nothing",
        )

        override fun help(context: Context): String =
            "Score one campaign directory against every maxim and record the verdicts."

        override fun run() {
            val recordsRoot = when (records) {
                null -> RepoRoot.records()
                "none" -> null
                else -> Path.of(records.orEmpty())
            }
            val text = try {
                score(campaignDir, referenceSet, deviceKey, sdkVersion, arm, ledger ?: defaultLedger().toString(), runId, now, recordsRoot)
            } catch (e: IllegalArgumentException) {
                echo(e.message, err = true)
                throw ProgramResult(1)
            }
            echo(text, trailingNewline = false)
        }
    }

    private class Render : CliktCommand(name = "render") {
        private val ledger by option(
            "--ledger",
            help = "ledger JSON to read (default: $DEFAULT_LEDGER_REL under the repo); 'none' for an empty one",
        )
        private val output by option("-o", "--output", help = "MAXIMS.md to write (default: beside the ledger); '-' for stdout")
        private val now by option("--now", help = "timestamp to stamp (tests)")

        override fun help(context: Context): String =
            "Render MAXIMS.md from the maxim definitions and the ledger."

        override fun run() {
            echo(render(ledger ?: defaultLedger().toString(), output ?: defaultDoc().toString(), now), trailingNewline = false)
        }
    }

    companion object {
        const val DEFAULT_LEDGER_REL: String = "${RepoRoot.RECORDS_REL}/maxims/ledger.json"
        const val DEFAULT_DOC_REL: String = "${RepoRoot.RECORDS_REL}/maxims/MAXIMS.md"

        fun defaultLedger(): Path = RepoRoot.locate().resolve(DEFAULT_LEDGER_REL)

        fun defaultDoc(): Path = RepoRoot.locate().resolve(DEFAULT_DOC_REL)

        /**
         * `cmd_score`: the complete stdout. [campaign] is a campaign directory or a `.zip` of one (the records
         * root's form, unpacked to a temporary directory). The ledger at [ledger] is updated on disk unless it
         * is `"none"`; when it is updated and [recordsRoot] is given, the campaign's datasets and log are packed
         * into `<recordsRoot>/campaigns/<run-id>.zip` (skipped when the campaign already lives under that root).
         * Throws [IllegalArgumentException] when the campaign holds no `pass1.json`.
         */
        @Suppress("LongParameterList")
        fun score(
            campaign: Path,
            referenceSet: Path?,
            deviceKey: String?,
            sdkVersion: String?,
            arm: String?,
            ledger: String,
            runId: String?,
            now: String?,
            recordsRoot: Path? = null,
        ): String {
            val ref = referenceSet?.let { StartupJson.parseToJsonElement(Files.readString(it)).jsonObject }
            val datasetsDir = materialize(campaign)
            val loaded = Maxims.loadCampaign(datasetsDir)
            val cell = Maxims.resolveCell(loaded, ref, deviceKey, sdkVersion, arm)
            val id = runId.takeUnless { it.isNullOrEmpty() } ?: campaignName(campaign)
            val verdicts = Maxims.score(loaded)
            val cands = Maxims.candidatesOf(loaded)
            val out = StringBuilder()
            out.append("maxims: ${cell.label} (run $id, ${loaded.iterations.size} iterations in ${loaded.passes.size} passes)\n")
            verdicts.forEach { (m, v) ->
                out.append("  ${v.status.padEnd(STATUS_W)} ${m.id.padEnd(ID_W)} ${v.observed}\n")
            }
            if (cands.isNotEmpty()) {
                out.append("  candidates: " + cands.joinToString(", ") { (name, lift) -> "$name ${PyFormat.fixed(lift, 2)}x" } + "\n")
            }
            if (ledger == "none") {
                out.append("ledger: not recorded (--ledger none)\n")
                return out.toString()
            }
            val path = Path.of(ledger)
            val led = MaximsLedger.record(MaximsLedger.load(path), id, cell, verdicts, cands, nowUtc(now))
            MaximsLedger.save(led, path)
            out.append("ledger: $ledger now ${MaximsLedger.runs(led)} runs\n")
            if (recordsRoot != null) {
                out.append(keepDatasets(campaign, datasetsDir, recordsRoot, id))
            }
            return out.toString()
        }

        /**
         * A campaign is a directory, or a `.zip` of one: unpack the archive into a temporary directory and
         * return it, else return the directory as it stands.
         */
        fun materialize(campaign: Path): Path {
            if (!Files.isRegularFile(campaign) || !campaign.fileName.toString().endsWith(".zip")) return campaign
            return Zips.unpackToTemp(campaign)
        }

        /**
         * Pack the campaign's per-pass datasets (`passN.json`, `passN-factors.json`, `passN-cohorts.json`, ...),
         * `campaign.log` and provenance files from [datasetsDir] into `<recordsRoot>/campaigns/<runId>.zip`
         * (flat, deflated), replacing an earlier archive. Returns the stdout line; a campaign whose [origin]
         * already lies under the records root is left where it is.
         */
        fun keepDatasets(origin: Path, datasetsDir: Path, recordsRoot: Path, runId: String): String {
            val root = realPath(recordsRoot)
            if (realPath(origin).startsWith(root)) {
                return "records: campaign already under $recordsRoot\n"
            }
            val dest = recordsRoot.resolve("campaigns").resolve("$runId.zip")
            val files = Files.list(datasetsDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && isDataset(it.fileName.toString()) }.sorted().toList()
            }
            Zips.pack(files, dest)
            return "records: ${files.size} files kept in $dest\n"
        }

        private fun isDataset(name: String): Boolean = name in DATASET_NAMES || DATASET_PATTERN.matches(name)

        private fun realPath(path: Path): Path =
            runCatching { path.toRealPath() }.getOrElse { path.toAbsolutePath().normalize() }

        /** `pathlib.Path(dir).resolve().name`, minus `.zip` for an archive. */
        private fun campaignName(campaign: Path): String = realName(campaign).removeSuffix(".zip")

        /** `cmd_render`: the document itself when [output] is `"-"`, else `wrote <path>` after writing it. */
        fun render(ledger: String, output: String, now: String?): String {
            val led = if (ledger != "none") MaximsLedger.load(Path.of(ledger)) else MaximsLedger.empty()
            val text = MaximsDoc.render(led, nowUtc(now))
            if (output == "-") {
                return text
            }
            val path = Path.of(output)
            path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            Files.writeString(path, text)
            return "wrote $output\n"
        }

        /** `explicit or time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())`. */
        fun nowUtc(explicit: String?): String =
            explicit.takeUnless { it.isNullOrEmpty() } ?: STAMP.format(Instant.now())

        /** `pathlib.Path(dir).resolve().name`. */
        private fun realName(dir: Path): String {
            val resolved = runCatching { dir.toRealPath() }.getOrElse { dir.toAbsolutePath().normalize() }
            return resolved.fileName?.toString() ?: ""
        }

        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        private val DATASET_NAMES = setOf("campaign.log", "run-metadata.json", "cell-state.json")
        private val DATASET_PATTERN = Regex("pass[0-9]+(-[a-z-]+)?\\.json")
        private const val STATUS_W = 12
        private const val ID_W = 22
    }
}
