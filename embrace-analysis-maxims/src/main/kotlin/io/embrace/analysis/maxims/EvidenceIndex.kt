package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.records.store.ArchiveHygiene
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.EvidenceClass
import io.embrace.analysis.stats.Quantile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * `campaigns/index.json`: one generated, plain-text entry per evidence archive, so a finding or a ledger
 * entry can cite a run by id and a reader can see what that run was without unpacking anything. It is
 * derived state - regenerate it, never edit it - and `records check-links` fails when it is stale.
 *
 * Per archive: the cell it was scored as (resolved exactly as `maxims score` resolves it, overrides from
 * `ledger-runs.json` included), the run's parameters (method, shape, commit), the summary statistics
 * (Type-7 quantiles over every launch, and each pass's median), the evidence class, and its place among
 * REPLICATES - archives of the same device key, SDK version, arm, method and shape, which are draws from
 * the same population and the raw material of the between-campaign variance check (`replicates`).
 */
object EvidenceIndex {

    const val FILE_NAME: String = "index.json"

    data class Entry(
        val runId: String,
        val deviceKey: String,
        val sdkVersion: String,
        val arm: String,
        val method: String?,
        val passes: Int,
        val iterations: Int?,
        val launches: Int,
        val started: String?,
        val repoHead: String?,
        val evidence: EvidenceClass.Verdict,
        val median: Double,
        val p90: Double,
        val p95: Double,
        val max: Double,
        val passMedians: List<Double>,
        val members: List<String>,
        val scored: Boolean,
    ) {
        /** Archives sharing this key measured the same population. */
        val replicateKey: String get() = "$deviceKey / $sdkVersion / $arm / ${method ?: "?"} / ${passes}x${iterations ?: "?"}"
        val rawMembers: List<String> get() = members.filterNot { ArchiveHygiene.isEvidence(it) }
    }

    data class Replicated(val entry: Entry, val replicateIndex: Int, val replicateOf: Int)

    fun file(records: Path): Path = records.resolve(CAMPAIGNS).resolve(FILE_NAME)

    /** The runs `ledger-runs.json` lists, by run id, with their cell overrides. */
    fun ledgerRuns(records: Path): Map<String, JsonObject> {
        val file = records.resolve("maxims").resolve("ledger-runs.json")
        if (!Files.isRegularFile(file)) return emptyMap()
        val runs = PyJson.arr(StartupJson.parseToJsonElement(Files.readString(file)).jsonObject, "runs").orEmpty()
        return runs.map { it.jsonObject }.associateBy { PyJson.str(it, "run", "") }
    }

    /** Every zip archive under `campaigns/` in [records], sorted by run id (the name without `.zip`). */
    fun archives(records: Path): List<Path> {
        val dir = records.resolve(CAMPAIGNS)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { s -> s.filter { it.fileName.toString().endsWith(".zip") }.toList() }
            .sortedBy { it.fileName.toString().removeSuffix(".zip") }
    }

    fun build(records: Path, serials: DeviceSerials.Serials = DeviceSerials.Serials.EMPTY): List<Replicated> {
        val refFile = records.resolve("longitudinal").resolve("reference-set.json")
        val ref = refFile.takeIf { Files.isRegularFile(it) }
            ?.let { StartupJson.parseToJsonElement(Files.readString(it)).jsonObject }
            ?.let { DeviceSerials.hydrate(it, DeviceSerials.merge(serials, it)) }
        val runs = ledgerRuns(records)
        val entries = archives(records).map { archive -> entryOf(archive, ref, runs) }
        return replicate(entries)
    }

    fun entryOf(archive: Path, ref: JsonObject?, runs: Map<String, JsonObject>): Entry {
        val runId = archive.fileName.toString().removeSuffix(".zip")
        val members = Zips.readAll(archive).keys.toList()
        val dir = Zips.unpackToTemp(archive)
        val loaded = Maxims.loadCampaign(dir)
        val override = runs[runId]
        val cell = Maxims.resolveCell(
            loaded,
            ref,
            PyJson.strOrNull(override, "device"),
            PyJson.strOrNull(override, "sdk"),
            PyJson.strOrNull(override, "arm"),
        )
        val meta = loaded.meta
        val windows = loaded.iterations.map { it.window }.sorted()
        val shape = PyJson.obj(meta, "run_shape")
        val declaredIterations = PyJson.double(shape, "iterations")?.toInt()
        val iterations = declaredIterations ?: loaded.passes.map { it.size }.distinct().singleOrNull()
        return Entry(
            runId = runId,
            deviceKey = cell.device,
            sdkVersion = cell.sdk,
            arm = cell.arm,
            method = PyJson.strOrNull(meta, "method"),
            passes = loaded.passes.size,
            iterations = iterations,
            launches = windows.size,
            started = PyJson.strOrNull(meta, "started"),
            repoHead = PyJson.strOrNull(meta, "repo_head"),
            evidence = EvidenceClass.of(meta, cell.sdk.takeIf { it != "unknown" }),
            median = Quantile.type7(windows, MEDIAN),
            p90 = Quantile.type7(windows, P90),
            p95 = Quantile.type7(windows, P95),
            max = windows.last(),
            passMedians = loaded.passes.map { p -> Quantile.median(p.map { it.window }.sorted()) },
            members = members,
            scored = runId in runs,
        )
    }

    /** Number the archives of each replicate key by start time (then run id), 1-based. */
    fun replicate(entries: List<Entry>): List<Replicated> {
        val groups = entries.groupBy { it.replicateKey }
        return entries.map { e ->
            val group = groups.getValue(e.replicateKey).sortedWith(compareBy({ it.started ?: "" }, { it.runId }))
            Replicated(e, group.indexOf(e) + 1, group.size)
        }
    }

    fun render(entries: List<Replicated>): String {
        val doc = JsonObject(
            mapOf(
                "_comment" to JsonPrimitive(
                    "Generated by `tools/startup records index` from the archives under campaigns/: never edit. One entry per evidence " +
                        "archive; `replicate` numbers the archives that sampled the same population.",
                ),
                "archives" to JsonPrimitive(entries.size),
                "entries" to JsonArray(entries.map { toJson(it) }),
            ),
        )
        return PyJson.dumpsPretty(doc) + "\n"
    }

    /** Write `campaigns/index.json`; returns the stdout line. */
    fun write(records: Path, serials: DeviceSerials.Serials = DeviceSerials.Serials.EMPTY): String {
        val entries = build(records, serials)
        val target = file(records)
        Files.createDirectories(target.parent)
        Files.writeString(target, render(entries))
        val raw = entries.count { it.entry.rawMembers.isNotEmpty() }
        val note = if (raw > 0) " ($raw carrying raw members - run `records cull`)" else ""
        return "records: indexed ${entries.size} archives in $target$note\n"
    }

    private fun toJson(r: Replicated): JsonObject {
        val e = r.entry
        val fields = LinkedHashMap<String, JsonElement>()
        fields["run_id"] = JsonPrimitive(e.runId)
        fields["archive"] = JsonPrimitive("$CAMPAIGNS/${e.runId}.zip")
        fields["cell"] = JsonObject(
            mapOf(
                "device_key" to JsonPrimitive(e.deviceKey),
                "sdk_version" to JsonPrimitive(e.sdkVersion),
                "arm" to JsonPrimitive(e.arm),
            ),
        )
        fields["method"] = e.method?.let { JsonPrimitive(it) } ?: JsonNull
        fields["run_shape"] = JsonObject(
            mapOf("passes" to JsonPrimitive(e.passes), "iterations" to (e.iterations?.let { JsonPrimitive(it) } ?: JsonNull)),
        )
        fields["launches"] = JsonPrimitive(e.launches)
        fields["started"] = e.started?.let { JsonPrimitive(it) } ?: JsonNull
        fields["repo_head"] = e.repoHead?.let { JsonPrimitive(it) } ?: JsonNull
        fields["evidence_class"] = JsonPrimitive(e.evidence.klass)
        if (e.evidence.reasons.isNotEmpty()) {
            fields["evidence_reasons"] = JsonArray(e.evidence.reasons.map { JsonPrimitive(it) })
        }
        fields["replicate"] = JsonObject(
            mapOf(
                "key" to JsonPrimitive(e.replicateKey),
                "index" to JsonPrimitive(r.replicateIndex),
                "of" to JsonPrimitive(r.replicateOf),
            ),
        )
        fields["summary"] = JsonObject(
            mapOf(
                "quantile" to JsonPrimitive("type7"),
                "median" to JsonPrimitive(round(e.median)),
                "p90" to JsonPrimitive(round(e.p90)),
                "p95" to JsonPrimitive(round(e.p95)),
                "max" to JsonPrimitive(round(e.max)),
                "pass_medians" to JsonArray(e.passMedians.map { JsonPrimitive(round(it)) }),
            ),
        )
        fields["scored"] = JsonPrimitive(e.scored)
        fields["members"] = JsonArray(e.members.map { JsonPrimitive(it) })
        if (e.rawMembers.isNotEmpty()) {
            fields["raw_members"] = JsonArray(e.rawMembers.map { JsonPrimitive(it) })
        }
        return JsonObject(fields)
    }

    private fun round(x: Double): Double = Math.rint(x * ROUNDING) / ROUNDING

    private const val CAMPAIGNS = "campaigns"
    private const val MEDIAN = 0.5
    private const val P90 = 0.9
    private const val P95 = 0.95
    private const val ROUNDING = 1000.0
}
