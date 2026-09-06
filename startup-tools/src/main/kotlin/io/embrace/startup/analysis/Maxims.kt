package io.embrace.startup.analysis

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.store.Ingest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import io.embrace.startup.analysis.Campaign as CampaignFiles

/**
 * `maxims`: the bench toolchain's beliefs about SDK init, checked on every campaign.
 *
 * Each maxim is a statement with a mechanical check that a campaign's data confirms, contradicts, or is
 * too thin to judge; the verdicts accumulate in the ledger ([io.embrace.startup.store.MaximsLedger]) per
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
        Maxim(
            id = "off-cpu",
            scope = UNIVERSAL,
            statement = "A slow init is one whose main thread was off-CPU: the lowest quartile of on-CPU share is at least " +
                "1.5x as likely to be slow.",
            why = "Init is main-thread work; when the thread is not running, something else is holding it. Same claim as " +
                "the production maxim, read from the trace's thread states instead of init-cpu-pct.",
            check = liftCheck({ it.cpuShare }, lowest = true, floor = 1.5, directional = false),
        ),
        Maxim(
            id = "scheduler-wait",
            scope = DIRECTIONAL,
            statement = "Any scheduler wait makes a slow init likelier, by 2x where the effect is measurable: the highest " +
                "quartile of runnable-wait share.",
            why = "A runnable thread that is not scheduled loses wall time the SDK cannot recover; how often that happens " +
                "is the device's. The bench proved the concurrent-CPU mechanism causal by inducing churn.",
            check = liftCheck({ it.rqShare }, lowest = false, floor = 2.0, directional = true),
        ),
        Maxim(
            id = "concurrent-cpu",
            scope = DIRECTIONAL,
            statement = "Concurrent CPU from other processes makes a slow init likelier, by 1.5x or more where measurable: " +
                "the highest quartile of other-process on-CPU share of the window, the idle task excluded.",
            why = "The bench's extreme-outlier class: system_server GC compactions, dexopt and app-ecosystem churn inflate " +
                "the window through the memory bus while the main thread's own wait stays small. Proven causal by " +
                "inducing churn; how much of it a device sees is the device's.",
            check = liftCheck({ it.competitorShare }, lowest = false, floor = 1.5, directional = true),
        ),
        Maxim(
            id = "starved-enriched",
            scope = UNIVERSAL,
            statement = "Starved inits (runnable-wait share of 20% or more) are at least 3x enriched among slow inits.",
            why = "Rare on a quiet bench, and decisive when it happens; the production tool uses the same 20% cutoff.",
            check = ::checkStarved,
        ),
        Maxim(
            id = "gc-rare",
            scope = UNIVERSAL,
            statement = "The SDK's own GC almost never runs during init: a collection of 1 ms or more inside the window on " +
                "2% or fewer of iterations on any device above 2 GB of RAM.",
            why = "Own-process collection during init was only ever seen on the 1 GB tier; above it a zero is the device, " +
                "not the detector. Production sees 0.02 to 0.3%. The 1 ms floor keeps sub-millisecond class-load slices " +
                "misread as GC in older datasets from counting.",
            check = ::checkGcRare,
        ),
        Maxim(
            id = "first-launch",
            scope = DIRECTIONAL,
            statement = "The first launch after an install is slower than the rest of its pass, by 1.15x or more where " +
                "measurable.",
            why = "Install aftermath: dexopt and system bursts. The bench also found the fresh-install config fast path " +
                "can cancel it on fast tiers, which is why this is directional and why a contradiction here is expected " +
                "on flagships.",
            check = ::checkFirstLaunch,
        ),
        Maxim(
            id = "config-fast-path",
            scope = UNIVERSAL,
            statement = "Every pass's first launch takes the fresh-install config fast path: its persisted-config-load is " +
                "below the median of the rest of the pass.",
            why = "iter000 has no cached config to decode. A violation means app data survived a failed uninstall and the " +
                "pass's first-launch sample is poisoned (hypothesis H3); the check is tier-relative because the two bands " +
                "scale with the device. An arm that clears app data before every launch is n/a: every launch takes the " +
                "fast path there, so the comparison has no second population.",
            check = ::checkConfigFastPath,
        ),
        Maxim(
            id = "compile-state-toggle",
            scope = DEVICE_SPECIFIC,
            statement = "Pass medians alternate between two levels at least 10% apart, with pure-CPU sections within 1.25x " +
                "and block-and-resume sections 1.5x or more between the two states.",
            why = "Install-time compile state alternating per reinstall on some OEM builds (hypothesis H2). Which devices " +
                "show it is the finding; compare only matching-state passes where they do.",
            check = ::checkToggle,
        ),
        Maxim(
            id = "restore-vs-create",
            scope = UNIVERSAL,
            statement = "A launch that creates a user session spends at least 2x as long in start-first-session as one " +
                "that restores the persisted session.",
            why = "The create path serializes and persists session metadata on the main thread; the restore path is a " +
                "read. The bench measured only the restore path until the cohort tap existed. An arm that pins the " +
                "cohort cannot test it - the comparison is then across the matching cells of the two arms, not inside " +
                "one campaign, and the check reports n/a rather than pretending the sample is merely thin.",
            check = ::checkRestoreVsCreate,
        ),
        Maxim(
            id = "cpu-closure",
            scope = UNIVERSAL,
            statement = "The init span's CPU attributes close: init-cpu-pct plus init-run-delay-pct is between 0 and 100 " +
                "on at least 98% of launches.",
            why = "The one attribute check that needs no ground truth; a violation means the attributes and the trace " +
                "disagree and the run is not trustworthy.",
            check = ::checkCpuClosure,
        ),
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
        val meta = Ingest.loadProvenance(runDir)?.first ?: JsonObject(emptyMap())
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
    fun candidatesOf(c: Campaign): List<Pair<String, Double>> {
        val factors = candidateFactors(c)
        val out = ArrayList<Pair<String, Double>>()
        factors.keys.sorted().forEach { name ->
            val (key, lowest) = factors.getValue(name)
            val group = quartile(c.iterations, key, lowest) ?: return@forEach
            val (lift, _) = liftOf(group, c.iterations)
            if (lift != null && lift >= CANDIDATE_LIFT) {
                out.add(name to lift)
            }
        }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Lifts
    // ------------------------------------------------------------------------------------------

    /** The lowest (or highest) quartile of iterations by key, ties broken by run order; null if thin. */
    private fun quartile(iterations: List<Iteration>, key: (Iteration) -> Double?, lowest: Boolean): List<Iteration>? {
        val valued = iterations.filter { key(it) != null }
        val k = valued.size / 4
        if (k < MIN_GROUP) {
            return null
        }
        // Sort order: by value first (floats compared with `<`/`==`, so -0.0 ties 0.0), then pass_no, then index.
        val ordered = valued.sortedWith { a, b ->
            val ka = requireNotNull(key(a))
            val kb = requireNotNull(key(b))
            when {
                ka < kb -> -1
                ka > kb -> 1
                a.passNo != b.passNo -> a.passNo.compareTo(b.passNo)
                else -> a.index.compareTo(b.index)
            }
        }
        return if (lowest) ordered.take(k) else ordered.takeLast(k)
    }

    /** `(lift, observed)` for a group, or `(null, reason)` when the campaign cannot carry one. */
    private fun liftOf(group: List<Iteration>, iterations: List<Iteration>): Pair<Double?, String> {
        val baseSlow = iterations.count { it.slow }
        if (baseSlow < MIN_SLOW) {
            return null to "only $baseSlow slow iterations of ${iterations.size}"
        }
        val gSlow = group.count { it.slow }
        val baseRate = baseSlow.toDouble() / iterations.size
        val lift = (gSlow.toDouble() / group.size) / baseRate
        return lift to "${PyFormat.fixed(lift, 2)}x on ${group.size} iterations " +
            "(slow $gSlow/${group.size} vs $baseSlow/${iterations.size})"
    }

    private fun directionalStatus(lift: Double, floor: Double): String = when {
        lift >= floor -> CONFIRMED
        lift < NULL_LOW -> CONTRADICTED
        else -> UNDETECTED
    }

    private fun liftCheck(
        key: (Iteration) -> Double?,
        lowest: Boolean,
        floor: Double,
        directional: Boolean,
        needsFactors: Boolean = true,
    ): (Campaign) -> Verdict = { c ->
        if (needsFactors && !c.hasFactors) {
            Verdict(NA, "no passN-factors.json")
        } else {
            val group = quartile(c.iterations, key, lowest)
            if (group == null) {
                Verdict(THIN, "quartile under $MIN_GROUP iterations")
            } else {
                val (lift, obs) = liftOf(group, c.iterations)
                when {
                    lift == null -> Verdict(THIN, obs)
                    directional -> Verdict(directionalStatus(lift, floor), obs)
                    lift >= floor -> Verdict(CONFIRMED, obs)
                    else -> Verdict(CONTRADICTED, obs)
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The checks
    // ------------------------------------------------------------------------------------------

    private fun checkStarved(c: Campaign): Verdict {
        if (!c.hasFactors) {
            return Verdict(NA, "no passN-factors.json")
        }
        val starved = c.iterations.filter { it.rqShare != null && it.rqShare >= STARVED_SHARE }
        val slow = c.iterations.filter { it.slow }
        if (starved.size < MIN_SLOW || slow.size < MIN_SLOW) {
            return Verdict(THIN, "${starved.size} starved and ${slow.size} slow iterations of ${c.iterations.size}")
        }
        val shareAll = starved.size.toDouble() / c.iterations.size
        val shareSlow = slow.count { it in starved }.toDouble() / slow.size
        val enrichment = shareSlow / shareAll
        val obs = "${PyFormat.fixed(enrichment, 2)}x enrichment, ${starved.size} starved iterations"
        return Verdict(if (enrichment >= ENRICHMENT_FLOOR) CONFIRMED else CONTRADICTED, obs)
    }

    private fun checkGcRare(c: Campaign): Verdict {
        if (c.ramClass == LOW_RAM_CLASS) {
            return Verdict(NA, "out of scope on a $LOW_RAM_CLASS device")
        }
        if (!c.hasFactors) {
            return Verdict(NA, "no passN-factors.json")
        }
        val n = c.iterations.size
        val withGc = c.iterations.count { it.gc == true }
        val share = withGc.toDouble() / n
        return Verdict(
            if (share <= GC_SHARE_MAX) CONFIRMED else CONTRADICTED,
            "${PyFormat.fixed(PERCENT * share, 1)}% of $n iterations ran own GC",
        )
    }

    private fun checkFirstLaunch(c: Campaign): Verdict {
        val ratios = ArrayList<Double>()
        c.passes.forEach { p ->
            if (p.size < MIN_PASS_FOR_FIRST_LAUNCH) {
                return@forEach
            }
            val rest = Quantile.median(p.drop(1).map { it.window }.sorted())
            if (rest > 0) {
                ratios.add(p[0].window / rest)
            }
        }
        if (ratios.size < 2) {
            return Verdict(THIN, "${ratios.size} passes with a first launch and a rest")
        }
        val ratio = Quantile.median(ratios.sorted())
        return Verdict(directionalStatus(ratio, NULL_HIGH), "iter000/rest ${PyFormat.fixed(ratio, 2)}x over ${ratios.size} passes")
    }

    /**
     * Tier-relative on purpose: the fresh path is a fraction of the cached path on every tier, but the
     * absolute numbers span an order of magnitude between flagship and entry.
     */
    private fun checkConfigFastPath(c: Campaign): Verdict {
        // The check reads iter000 against the rest because only iter000 starts without a cached config.
        // An arm that wipes app data before every launch gives every launch the fast path, so the two
        // sides are the same population and the comparison is noise either way.
        dataResetMethod(c)?.let {
            return Verdict(NA, "$it clears app data before every launch: all take the fast path")
        }
        val bad = ArrayList<String>()
        var seen = 0
        c.passes.forEach { p ->
            val first = p[0].dur[CFGLOAD]
            val rest = p.drop(1).mapNotNull { it.dur[CFGLOAD] }
            if (first == null || rest.isEmpty()) {
                return@forEach
            }
            seen += 1
            val rmed = Quantile.median(rest.sorted())
            if (first >= rmed) {
                bad.add("pass ${p[0].passNo}: iter000 ${PyFormat.fixed(first, 1)} ms vs rest p50 ${PyFormat.fixed(rmed, 1)} ms")
            }
        }
        if (seen == 0) {
            return Verdict(NA, "$CFGLOAD not in the datasets")
        }
        if (bad.isNotEmpty()) {
            return Verdict(CONTRADICTED, "${bad.size} of $seen passes; " + bad[0])
        }
        return Verdict(CONFIRMED, "every pass's iter000 below the rest's median over $seen passes")
    }

    private fun medSection(p: List<Iteration>, name: String): Double? {
        val vals = p.mapNotNull { it.dur[name] }
        return if (vals.isEmpty()) null else Quantile.median(vals.sorted())
    }

    private fun checkToggle(c: Campaign): Verdict {
        val meds = c.passes.map { p -> Quantile.median(p.map { it.window }.sorted()) }
        if (meds.size < MIN_PASSES_FOR_TOGGLE) {
            return Verdict(THIN, "${meds.size} passes; the toggle needs at least 3")
        }
        val fast = c.passes[meds.indices.minByOrNull { meds[it] } ?: 0]
        val slow = c.passes[meds.indices.maxByOrNull { meds[it] } ?: 0]
        val pure = sectionRatio(fast, slow, PURE_CPU)
        val block = sectionRatio(fast, slow, BLOCK_RESUME)
        val seq = meds.joinToString(" -> ") { PyFormat.fixed(it, 1) }
        if (pure == null || block == null) {
            return Verdict(NA, "pass medians $seq; section medians missing")
        }
        val obs = "pass medians $seq; pure-CPU ${PyFormat.fixed(pure, 2)}x, block-resume ${PyFormat.fixed(block, 2)}x"
        val fingerprint = pure <= PURE_CPU_MAX_RATIO && block >= BLOCK_RESUME_MIN_RATIO
        return Verdict(if (isAlternating(meds) && fingerprint) CONFIRMED else CONTRADICTED, obs)
    }

    /** Every step between consecutive pass medians is at least [TOGGLE_STEP] and the steps alternate in sign. */
    private fun isAlternating(meds: List<Double>): Boolean {
        val steps = (0 until meds.size - 1).map { meds[it + 1] / meds[it] - 1.0 }
        return steps.all { abs(it) >= TOGGLE_STEP } && (0 until steps.size - 1).all { (steps[it] > 0) != (steps[it + 1] > 0) }
    }

    /** Median over [names] of the slow-pass / fast-pass section-median ratio; null when no section carries one. */
    private fun sectionRatio(fastPass: List<Iteration>, slowPass: List<Iteration>, names: List<String>): Double? {
        val rs = names.mapNotNull { name ->
            val f = medSection(fastPass, name)
            val s = medSection(slowPass, name)
            if (f != null && s != null && f > 0) {
                s / f
            } else {
                null
            }
        }
        return if (rs.isEmpty()) null else Quantile.median(rs.sorted())
    }

    /**
     * The provenance's benchmark method when it is one that clears the app's data before every launch
     * (rather than once per pass), else null. Named rather than inferred: the harness owns these method
     * names, and the alternative - guessing from the data - cannot tell "every launch took the fast path"
     * from "the decode is simply cheap on this device".
     */
    private fun dataResetMethod(c: Campaign): String? {
        val method = PyJson.strOrNull(c.meta, "method") ?: return null
        return method.takeIf { m -> DATA_RESET_METHODS.any { m.contains(it) } }
    }

    /** Parses a launch attribute as a float, tolerantly: null when absent, `null`, or not a number. */
    private fun attrFloat(launch: JsonObject, key: String): Double? {
        val raw = launch[key] as? JsonPrimitive ?: return null
        if (raw is JsonNull) {
            return null
        }
        if (!raw.isString && raw.content == "true") {
            return 1.0
        }
        if (!raw.isString && raw.content == "false") {
            return 0.0
        }
        return raw.content.trim().toDoubleOrNull()
    }

    private fun checkRestoreVsCreate(c: Campaign): Verdict {
        if (c.cohorts.isEmpty()) {
            return Verdict(NA, "no passN-cohorts.json (EmbVerify tap not armed)")
        }
        val created = c.cohorts.filter { PyJson.strOrNull(it, "cohort") == "created" }
            .mapNotNull { attrFloat(it, FIRST_SESSION_ATTR) }
        val restored = c.cohorts.filter { PyJson.strOrNull(it, "cohort") == "restored" }
            .mapNotNull { attrFloat(it, FIRST_SESSION_ATTR) }
        if (created.isEmpty() && restored.isEmpty()) {
            return Verdict(NA, "$FIRST_SESSION_ATTR absent from the tap")
        }
        // An arm that pins the cohort holds one side of the comparison at zero by construction, so no
        // number of extra launches would make it testable: it needs the matching cell of the other arm.
        if (created.isEmpty() || restored.isEmpty()) {
            return Verdict(
                NA,
                "single-cohort arm (${created.size} created, ${restored.size} restored); " +
                    "compare against the matching cell of the other arm",
            )
        }
        if (created.size < MIN_COHORT || restored.size < MIN_COHORT) {
            return Verdict(THIN, "${created.size} created and ${restored.size} restored launches with the attribute")
        }
        val cMed = Quantile.median(created.sorted())
        val rMed = Quantile.median(restored.sorted())
        val obs = "created p50 ${PyFormat.fixed(cMed, 2)} ms (n=${created.size}) vs " +
            "restored p50 ${PyFormat.fixed(rMed, 2)} ms (n=${restored.size})"
        val ok = cMed >= 2.0 * rMed && (cMed - rMed) >= CREATE_MIN_GAP_MS
        return Verdict(if (ok) CONFIRMED else CONTRADICTED, obs)
    }

    private fun checkCpuClosure(c: Campaign): Verdict {
        if (c.cohorts.isEmpty()) {
            return Verdict(NA, "no passN-cohorts.json (EmbVerify tap not armed)")
        }
        val pairs = c.cohorts.mapNotNull { l ->
            val a = attrFloat(l, CPU_ATTR)
            val b = attrFloat(l, DELAY_ATTR)
            if (a != null && b != null) a to b else null
        }
        if (pairs.isEmpty()) {
            return Verdict(NA, "$CPU_ATTR / $DELAY_ATTR absent from the tap")
        }
        if (pairs.size < MIN_GROUP) {
            return Verdict(THIN, "${pairs.size} launches carry both attributes")
        }
        val ok = pairs.count { (a, b) ->
            val residual = PERCENT - a - b
            residual >= 0.0 && residual <= PERCENT
        }
        val obs = "$ok/${pairs.size} launches close to 100%"
        return Verdict(if (ok.toDouble() / pairs.size >= CLOSURE_MIN_SHARE) CONFIRMED else CONTRADICTED, obs)
    }

    // ------------------------------------------------------------------------------------------
    // Candidates: strong associations no maxim covers
    // ------------------------------------------------------------------------------------------

    /**
     * name -> (key function, lowest?) for every per-iteration factor present on all iterations. Every
     * duration or count is divided by the iteration's window first: a slow iteration is a long window, and
     * anything that accumulates over the window would correlate with slowness by construction.
     */
    private fun candidateFactors(c: Campaign): Map<String, Pair<(Iteration) -> Double?, Boolean>> {
        if (!c.hasFactors) {
            return emptyMap()
        }
        val its = c.iterations
        val factors = LinkedHashMap<String, Pair<(Iteration) -> Double?, Boolean>>()
        factors["D+io share"] = stateShare { k -> k.startsWith("D") && "+io" in k } to false
        factors["S share"] = stateShare { k -> k.startsWith("S") } to false
        factors["lock_contention share"] = scalarShare { it.lockContentionMs } to false
        factors["art_classload share"] = scalarShare { it.artClassloadMs } to false
        factors["art_verify share"] = scalarShare { it.artVerifyMs } to false
        factors["binder_txn per ms"] = scalarShare { it.binderTxnCnt } to false
        factors["eff_mhz"] = scalar { it.effMhz } to true
        factors["mem_available"] = scalar { it.memAvailable } to true
        competitorMeans(its).forEach { (name, mean) ->
            if (mean >= COMPETITOR_MIN_SHARE) {
                factors["othercpu:$name share"] = competitorShare(name) to false
            }
        }
        return factors.filter { (_, kv) -> its.all { kv.first(it) != null } }
    }

    /** Each competing process's mean share of the window across the campaign, the idle task excluded. */
    private fun competitorMeans(its: List<Iteration>): Map<String, Double> {
        val means = LinkedHashMap<String, Double>()
        its.forEach { iter ->
            requireNotNull(iter.factors).othercpu.forEach { (name, v) ->
                if (name != IDLE_TASK) {
                    means[name] = (means[name] ?: 0.0) + v / iter.window / its.size
                }
            }
        }
        return means
    }

    private fun scalarShare(get: (OutlierFactors.Record) -> Double?): (Iteration) -> Double? =
        { iter -> get(requireNotNull(iter.factors))?.let { v -> v / iter.window } }

    private fun scalar(get: (OutlierFactors.Record) -> Double?): (Iteration) -> Double? =
        { iter -> get(requireNotNull(iter.factors)) }

    /** Summed ms of the thread states whose name (before any `:blocked_function`) satisfies [pred], over the window. */
    private fun stateShare(pred: (String) -> Boolean): (Iteration) -> Double? = { iter ->
        val states = requireNotNull(iter.factors).states
        states.entries.filter { e -> pred(e.key.substringBefore(':')) }.sumOf { e -> e.value } / iter.window
    }

    private fun competitorShare(name: String): (Iteration) -> Double? =
        { iter -> (requireNotNull(iter.factors).othercpu[name] ?: 0.0) / iter.window }

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

    /**
     * Fragments of the harness method names that clear the app's data before EVERY launch, not once per
     * pass. Add one here when a new arm does the same, or `config-fast-path` will contradict on it for a
     * reason that is the arm's design rather than a finding.
     */
    private val DATA_RESET_METHODS = listOf("NewUserSession")
    private const val MAX_PASS_NUMBER = 100
    private const val PERCENT = 100.0
    private const val ENRICHMENT_FLOOR = 3.0
    private const val GC_SHARE_MAX = 0.02
    private const val MIN_PASS_FOR_FIRST_LAUNCH = 3
    private const val MIN_PASSES_FOR_TOGGLE = 3
    private const val PURE_CPU_MAX_RATIO = 1.25
    private const val BLOCK_RESUME_MIN_RATIO = 1.5
    private const val MIN_COHORT = 5
    private const val CREATE_MIN_GAP_MS = 0.5
    private const val CLOSURE_MIN_SHARE = 0.98
}
