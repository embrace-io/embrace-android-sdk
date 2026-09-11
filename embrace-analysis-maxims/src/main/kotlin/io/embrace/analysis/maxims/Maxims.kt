package io.embrace.analysis.maxims

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.records.Provenance
import io.embrace.analysis.reports.HypothesisTests
import io.embrace.analysis.reports.OutlierFactors
import io.embrace.analysis.reports.VarianceAnalysis
import io.embrace.analysis.stats.Quantile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import io.embrace.analysis.reports.Campaign as CampaignFiles

/**
 * `maxims`: the bench toolchain's beliefs about SDK init, checked on every campaign.
 *
 * Each maxim is a statement with a mechanical check that a campaign's data confirms, contradicts, or is
 * too thin to judge; the verdicts accumulate in the ledger ([MaximsLedger]) per
 * cell - one device under one recipe and arm. Strong associations no maxim covers come out as
 * candidates. Inputs are the per-pass datasets a campaign already produces (`passN.json`,
 * `passN-factors.json`, `passN-cohorts.json`) plus `run-metadata.json` / `cell-state.json` for
 * provenance; no trace is opened here. Every threshold, string and ordering is pinned by the goldens.
 */
object Maxims {

    const val SOURCE: String = "bench"

    // Verdicts, exactly the production tool's vocabulary.
    const val CONFIRMED: String = "confirmed"
    const val CONTRADICTED: String = "contradicted"
    const val THIN: String = "thin"
    const val UNDETECTED: String = "undetected"
    const val NA: String = "n/a"

    const val UNIVERSAL: String = "universal"
    const val DIRECTIONAL: String = "directional"
    const val DEVICE_SPECIFIC: String = "device-specific"

    /** The null band: lifts read as "no effect" - the same band the production tool uses. */
    const val NULL_LOW: Double = 0.85
    const val NULL_HIGH: Double = 1.15

    /** Power floors: a quartile group under [MIN_GROUP] or a campaign under [MIN_SLOW] slow iterations is thin. */
    const val MIN_GROUP: Int = 8
    const val MIN_SLOW: Int = 4

    /** "Slow": window minus the PASS median above max(4 ms, 10% of the pass median), as `hypothesis-tests`. */
    const val SLOW_FLOOR_MS: Double = 4.0
    const val SLOW_FRACTION: Double = 0.10

    const val STARVED_SHARE: Double = 0.20
    const val TOGGLE_STEP: Double = 0.10
    const val LOW_RAM_CLASS: String = "<=2GB"

    /** A real collection inside the window is milliseconds long; older datasets carry sub-ms false matches. */
    const val GC_REAL_MS: Double = 1.0
    const val CANDIDATE_LIFT: Double = 1.5

    /** A competing process is a candidate factor only at this mean share of the window; `swapper` never is. */
    const val COMPETITOR_MIN_SHARE: Double = 0.10
    const val IDLE_TASK: String = "swapper"
    const val MAX_CONTRADICTIONS: Int = 40

    val PURE_CPU: List<String> = HypothesisTests.PURE_CPU
    val BLOCK_RESUME: List<String> = HypothesisTests.BLOCK_RESUME
    const val CFGLOAD: String = "emb-persisted-config-load"
    const val FIRST_SESSION_ATTR: String = "start-first-session-duration-ms"
    const val CPU_ATTR: String = "init-cpu-pct"
    const val DELAY_ATTR: String = "init-run-delay-pct"

    /** One launch of the campaign with everything the checks read, in run order. Compared by identity, as the goldens require. */
    class Iteration(
        val passNo: Int,
        val index: Int,
        record: VarianceAnalysis.Record,
        val factors: OutlierFactors.Record?,
    ) {
        val window: Double = requireNotNull(record.windowMs) { "${record.trace} has no window_ms" }
        val dur: Map<String, Double> = record.dur
        val cpuShare: Double?
        val rqShare: Double?
        val gc: Boolean?
        val competitorShare: Double?

        /** Set once the pass median is known. */
        var slow: Boolean = false

        init {
            if (factors != null && window > 0) {
                val states = factors.states
                val running = states["Running"] ?: 0.0
                val runnable = states.entries.filter { it.key.substringBefore(':') in RUNNABLE_STATES }.sumOf { it.value }
                cpuShare = running / window
                rqShare = runnable / window
                gc = (factors.gcSliceMs ?: 0.0) >= GC_REAL_MS
                competitorShare = factors.othercpu.entries.filter { it.key != IDLE_TASK }.sumOf { it.value } / window
            } else {
                cpuShare = null
                rqShare = null
                gc = null
                competitorShare = null
            }
        }
    }

    class Campaign(
        val runDir: Path,
        /** Lists of [Iteration] per pass. */
        val passes: List<List<Iteration>>,
        /** Launch records across passes, or empty when the EmbVerify tap was not armed. */
        val cohorts: List<JsonObject>,
        val meta: JsonObject,
        var ramClass: String?,
    ) {
        val iterations: List<Iteration> = passes.flatten()
        val hasFactors: Boolean get() = iterations.all { it.factors != null }

        init {
            passes.forEach { p ->
                val med = Quantile.median(p.map { it.window }.sorted())
                val cut = maxOf(SLOW_FLOOR_MS, SLOW_FRACTION * med)
                p.forEach { it.slow = (it.window - med) > cut }
            }
        }
    }

    /** The cell a campaign is scored as: device x SDK version x arm. */
    data class Cell(val device: String, val sdk: String, val arm: String) {
        val label: String get() = "$device / $sdk / $arm"
    }

    data class Verdict(val status: String, val observed: String)

    class Maxim(
        val id: String,
        val scope: String,
        val statement: String,
        val why: String,
        val check: (Campaign) -> Verdict,
    )

    /** Every maxim, in the order `score` reports and MAXIMS.md lists them. */
    val ALL: List<Maxim> = listOf(
        OffCpuMaxim.MAXIM,
        SchedulerWaitMaxim.MAXIM,
        ConcurrentCpuMaxim.MAXIM,
        StarvedEnrichedMaxim.MAXIM,
        GcRareMaxim.MAXIM,
        FirstLaunchMaxim.MAXIM,
        ConfigFastPathMaxim.MAXIM,
        CompileStateToggleMaxim.MAXIM,
        RestoreVsCreateMaxim.MAXIM,
        CpuClosureMaxim.MAXIM,
    )

    /** `pass1.json .. passN.json` up to the first missing number, with factors and cohorts where present. */
    fun loadCampaign(runDir: Path, ramClass: String? = null): Campaign {
        val passes = ArrayList<List<Iteration>>()
        val cohorts = ArrayList<JsonObject>()
        for (i in 1 until MAX_PASS_NUMBER) {
            val dataPath = runDir.resolve("pass$i.json")
            if (!Files.exists(dataPath)) {
                break
            }
            val data = CampaignFiles.readVariance(dataPath)
            val facPath = runDir.resolve("pass$i-factors.json")
            var factors: List<OutlierFactors.Record>? = if (Files.exists(facPath)) CampaignFiles.readFactors(facPath) else null
            if (factors != null && factors.size != data.size) {
                factors = null // misaligned datasets are worse than none
            }
            passes.add(data.mapIndexed { j, rec -> Iteration(i, j, rec, factors?.get(j)) })
            val cohPath = runDir.resolve("pass$i-cohorts.json")
            if (Files.exists(cohPath)) {
                val tap = StartupJson.parseToJsonElement(Files.readString(cohPath)).jsonObject
                PyJson.arr(tap, "launches")?.forEach { cohorts.add(it.jsonObject) }
            }
        }
        require(passes.isNotEmpty()) { "no pass1.json under $runDir - run `variance --json` per pass first" }
        val meta = Provenance.load(runDir)?.first ?: JsonObject(emptyMap())
        val ram = ramClass ?: PyJson.strOrNull(PyJson.obj(meta, "device_profile"), "ram_class")
        return Campaign(runDir, passes, cohorts, meta, ram)
    }

    /**
     * The device comes from the reference set via the run's serial, the version and arm from provenance;
     * each can be overridden for campaigns recorded before provenance was written.
     */
    fun resolveCell(
        campaign: Campaign,
        ref: JsonObject?,
        deviceKey: String?,
        sdkVersion: String? = null,
        arm: String? = null,
    ): Cell {
        val meta = campaign.meta
        // An empty reference set is treated as absent: no serial lookup, no RAM-class fallback.
        val devices = if (ref.isNullOrEmpty()) null else PyJson.obj(ref, "devices")
        val key = deviceKey.takeUnless { it.isNullOrEmpty() } ?: keyForSerial(devices, PyJson.strOrNull(meta, "serial"))
        val sdk = sdkVersion.takeUnless { it.isNullOrEmpty() }
            ?: truthyText(PyJson.obj(meta, "checks"), "sdk matches")
            ?: truthyText(meta, "sdk_version")
            ?: "unknown"
        val levels = PyJson.obj(PyJson.obj(meta, "cell"), "levels")
        val armLabel = arm.takeUnless { it.isNullOrEmpty() } ?: truthyText(levels, "compile") ?: "default"
        val refDevice = key?.let { devices?.get(it) as? JsonObject }
        if (campaign.ramClass == null && refDevice != null) {
            campaign.ramClass = PyJson.strOrNull(PyJson.obj(refDevice, "profile"), "ram_class")
        }
        return Cell(key ?: "unknown", sdk, armLabel)
    }

    fun score(campaign: Campaign): List<Pair<Maxim, Verdict>> = ALL.map { it to it.check(campaign) }

    /** `(name, lift)` for every candidate factor whose quartile lift reaches [CANDIDATE_LIFT], sorted by name. */
    fun candidatesOf(c: Campaign) = Candidates.of(c)

    /** `ref.get("devices")` entry whose serial matches; the first match wins. */
    private fun keyForSerial(devices: JsonObject?, serial: String?): String? {
        if (devices == null || serial.isNullOrEmpty()) {
            return null
        }
        return devices.entries.firstOrNull { PyJson.strOrNull(it.value as? JsonObject, "serial") == serial }?.key
    }

    /** `x or None`-style read of a provenance field: the text of a truthy value, else null. */
    private fun truthyText(obj: JsonObject?, key: String): String? =
        if (PyJson.truthy(obj, key)) PyJson.strOrNull(obj, key) else null

    private val RUNNABLE_STATES = setOf("R", "R+")
    private const val MAX_PASS_NUMBER = 100
}
