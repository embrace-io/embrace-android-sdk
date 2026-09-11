package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.records.store.RecordsRoot
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Scoring a campaign into the maxims ledger, and rendering the document from it: the workflow behind
 * `maxims score` / `maxims render`, and the step `records rebuild-ledger` repeats for every campaign.
 *
 * It lives here rather than in the command class because it is the domain step, not the argument
 * parsing: `Maxims` decides the verdicts, [MaximsLedger] holds them, and this joins the two with the
 * campaign's datasets. Nothing here touches Clikt, so it can be driven from a test or another command.
 */
object MaximsRunner {

    const val DEFAULT_LEDGER_REL: String = "${RecordsRoot.REL}/maxims/ledger.json"
    const val DEFAULT_DOC_REL: String = "${RecordsRoot.REL}/maxims/MAXIMS.md"

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

    private fun isDataset(name: String): Boolean = name in DATASET_NAMES || DATASET_PATTERN.matches(name)

    private fun realPath(path: Path): Path =
        runCatching { path.toRealPath() }.getOrElse { path.toAbsolutePath().normalize() }

    /** `pathlib.Path(dir).resolve().name`, minus `.zip` for an archive. */
    private fun campaignName(campaign: Path): String = realName(campaign).removeSuffix(".zip")

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
