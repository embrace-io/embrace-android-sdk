package io.embrace.startup.store

import io.embrace.startup.core.json.Derived
import io.embrace.startup.core.json.DeviceProfile
import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.Recipe
import io.embrace.startup.core.json.ReferenceSet
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.json.StoreRecord
import io.embrace.startup.core.stats.Derive
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.perfetto.Prebuilt
import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceHealth
import io.embrace.startup.perfetto.TraceProcessor
import io.embrace.startup.perfetto.TraceReads
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import io.embrace.startup.core.json.TraceHealth as StoredHealth

/**
 * `ingest_run.py`: turn one completed run directory into a longitudinal-store record, WITH validation.
 *
 * Validation is the point. A record that cannot be compared later is worse than no record, so this
 * refuses (rather than quietly stores) runs whose device is not in the reference set, whose profile
 * has drifted, whose recipe differs from the frozen one, or whose shape is not the declared one -
 * unless `--force`, which stamps the reasons into the record. Every guard here was added after a
 * real incident; the messages carry the incident so the next reader knows why.
 *
 * Two deliberate departures from the Python, both recorded in the port log:
 * 1. The health canary for the composed window is `emb-modules-init`, not the literal instrument
 *    name. The Python passed `"composed"` as the canary, no such slice exists, so no trace was ever
 *    judged clean and `signals_present` is `[]` in EVERY record of the real store.
 * 2. A run with no usable windows is refused even with `--force`. The Python's own message says "do
 *    not store a placeholder", but its code stored `derived: {}` when forced.
 */
object Ingest {

    const val DEFAULT_LOSSY_TOLERANCE_PCT: Double = 2.0
    const val COMPOSED_CANARY: String = "emb-modules-init"
    private const val TRUNCATED_BELOW = 0.9
    private const val OVER_LONG_ABOVE = 1.1
    private const val PERCENT = 100.0
    private const val WINDOW_DP = 3

    data class Options(
        val deviceKey: String? = null,
        val lossyTolerancePct: Double = DEFAULT_LOSSY_TOLERANCE_PCT,
        val force: Boolean = false,
        val notBaseline: Boolean = false,
    )

    /** What one trace contributed: its health verdict, its window (null when absent/unusable) and, if taken, the signal inventory. */
    data class Measurement(
        val trace: Path,
        val health: TraceHealth.Report,
        val windowMs: Double?,
        val signals: List<String>?,
    )

    data class Outcome(
        /** The record to append, or null when refused. */
        val record: StoreRecord?,
        val problems: List<String>,
        /** Informational lines the Python printed before the verdict (the lossy NOTE). */
        val notes: List<String>,
        val refusedOutright: String? = null,
    )

    fun canaryFor(instrument: String): String =
        if (instrument == Queries.COMPOSED_INSTRUMENT) COMPOSED_CANARY else instrument

    /** `*.perfetto-trace` recursively (sorted) then `*.pftrace` (sorted), as the Python listed them. */
    fun listTraces(runDir: Path): List<Path> {
        fun find(suffix: String) = Files.walk(runDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(suffix) }.toList()
        }.sorted()
        return find(".perfetto-trace") + find(".pftrace")
    }

    /** Provenance from whichever skill produced the run: `cell-state.json` first, then `run-metadata.json`. */
    fun loadProvenance(runDir: Path): Pair<JsonObject, String>? {
        listOf("cell-state.json", "run-metadata.json").forEach { name ->
            val found = Files.walk(runDir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString() == name }.toList()
            }.sorted()
            found.firstOrNull()?.let { return StartupJson.parseToJsonElement(Files.readString(it)).jsonObject to name }
        }
        return null
    }

    /**
     * Health, window and (from the first clean trace) signals, per trace - the only step that touches
     * Perfetto. Each trace is loaded ONCE into a warm session for its three queries (the Python parsed
     * it three times); the `-q` fallback is automatic if a session cannot start.
     */
    fun measure(tp: TraceProcessor, traces: List<Path>, instrument: String): List<Measurement> {
        val canary = canaryFor(instrument)
        var signalsTaken = false
        return traces.map { trace ->
            tp.withWarmTrace(trace) { target ->
                val health = TraceHealth.evaluate(trace.toString(), TraceHealth.parseRows(target.queryRaw(TraceHealth.sql(canary)).stdout))
                if (health.verdict == TraceHealth.Verdict.UNUSABLE) {
                    return@withWarmTrace Measurement(trace, health, null, null)
                }
                val window = TraceReads.parseWindow(target.queryRaw(Queries.windowFor(instrument)).stdout)?.takeIf { it != 0.0 }
                val signals = if (window != null && !signalsTaken && health.verdict == TraceHealth.Verdict.OK) {
                    signalsTaken = true
                    TraceReads.parseSignals(target.queryRaw(Queries.SIGNALS).stdout)
                } else {
                    null
                }
                Measurement(trace, health, window, signals)
            }
        }
    }

    /** A published, immutable version may seed a baseline; anything snapshot/local/dirty is comparison-only. */
    fun isPublished(sdkVersion: String?, forcedNotBaseline: Boolean = false): Boolean {
        if (forcedNotBaseline) return false
        val text = (sdkVersion ?: "").lowercase()
        if (text.isEmpty()) return false
        return listOf("snapshot", "local", "dirty", "+").none { it in text }
    }

    /** Python `round(w, 3)`: half-even on the exact binary value. */
    fun roundWindow(w: Double): Double = BigDecimal(w).setScale(WINDOW_DP, RoundingMode.HALF_EVEN).toDouble()

    @Suppress("LongParameterList")
    fun build(
        runDir: Path,
        ref: ReferenceSet,
        provenance: Pair<JsonObject, String>?,
        measurements: List<Measurement>,
        tpPath: Path?,
        options: Options,
        ingestedAt: String,
    ): Outcome {
        val instrument = ref.recipe.instrument
            ?: return Outcome(
                null,
                emptyList(),
                emptyList(),
                refusedOutright = "REFUSED: reference set has no instrument. The probe writes null deliberately - " +
                    "set recipe.instrument to what your traces actually contain (\"emb-sdk-start\" " +
                    "for SDK >= 9.2.0, \"composed\" for the fallback window) before the first ingest.",
            )
        val prov = provenance?.first
        val problems = ArrayList<String>()
        val notes = ArrayList<String>()

        val deviceKey = resolveDeviceKey(ref, prov, options, problems)
        val profileRun = PyJson.obj(prov, "device_profile") ?: JsonObject(emptyMap())
        checkProfileDrift(ref, deviceKey, profileRun, problems)
        val (buildType, compileState) = recipeOfRun(ref, prov, problems)

        val tally = Tally(measurements)
        shapeAndHealthProblems(tally, ref, instrument, options, problems, notes)
        if (tally.windows.isEmpty()) {
            return Outcome(null, problems, notes, refusedOutright = null)
        }
        val sdkVersion = PyJson.strOrNull(PyJson.obj(prov, "checks"), "sdk matches") ?: PyJson.strOrNull(prov, "sdk_version")
        val recipe = Recipe(
            buildType = buildType,
            compileState = compileState,
            instrument = instrument,
            runShape = ref.recipe.runShape,
            traceProcessorVersion = Prebuilt.VERSION,
            traceProcessorPath = tpPath?.toString(),
        )
        val record = StoreRecord(
            runId = PyJson.strOrNull(prov, "plan_run_id") ?: runDir.fileName.toString(),
            ingestedAt = ingestedAt,
            measuredAt = PyJson.strOrNull(prov, "started") ?: PyJson.strOrNull(prov, "measured_at"),
            deviceKey = deviceKey ?: "unknown",
            deviceProfile = StartupJson.decodeFromJsonElement(DeviceProfile.serializer(), profileRun),
            sdkVersion = sdkVersion,
            appBuildId = PyJson.strOrNull(prov, "apk_sha256"),
            recipe = recipe,
            conditions = PyJson.obj(PyJson.obj(prov, "cell"), "levels") ?: JsonObject(emptyMap()),
            baselineEligible = isPublished(sdkVersion, options.notBaseline),
            signalsPresent = tally.signals,
            traceHealth = StoredHealth(tally.traces, tally.lossy, tally.parseErrors, tally.signals.isNotEmpty()),
            windowsMs = tally.windows.map { roundWindow(it) },
            derived = checkNotNull(Derive.of(tally.windows)),
            sourceSkill = provenance?.second ?: "unknown",
            notes = if (problems.isNotEmpty()) "FORCED ingest despite: " + problems.joinToString("; ") else "",
        )
        return Outcome(record, problems, notes)
    }

    /** The run-level counts the Python accumulated in its trace loop. */
    private class Tally(measurements: List<Measurement>) {
        val traces: Int = measurements.size
        val windows: List<Double> = measurements.mapNotNull { it.windowMs }
        val signals: List<String> = measurements.firstNotNullOfOrNull { it.signals } ?: emptyList()
        val lossy: Int = measurements.count {
            it.health.verdict == TraceHealth.Verdict.LOSSY || it.health.verdict == TraceHealth.Verdict.UNUSABLE
        }
        val parseErrors: Int = measurements.count { it.health.verdict == TraceHealth.Verdict.SLICES_INCOMPLETE }
    }

    /** `f"\n{run_id} -> device_key=... n=... median=... p90=... max=..."` */
    fun summaryLine(record: StoreRecord): String {
        val d: Derived = record.derived
        return "\n${record.runId} -> device_key=${record.deviceKey} n=${d.n} median=${PyFormat.fixed(d.median, 1)} " +
            "p90=${PyFormat.fixed(d.p90, 1)} max=${PyFormat.fixed(d.max, 1)}"
    }

    fun append(store: Path, record: StoreRecord) {
        store.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            store,
            StartupJson.encodeToString(StoreRecord.serializer(), record) + "\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private fun resolveDeviceKey(ref: ReferenceSet, prov: JsonObject?, options: Options, problems: MutableList<String>): String? {
        var deviceKey = options.deviceKey
        val serial = PyJson.strOrNull(prov, "serial")
        if (deviceKey == null && serial != null) {
            deviceKey = ref.devices.entries.firstOrNull { it.value.serial == serial }?.key
        }
        if (deviceKey == null) {
            problems.add(
                "cannot map this run to a device_key in the reference set " +
                    "(pass --device-key, or ingest a run whose provenance records the serial)",
            )
        } else if (ref.devices.isNotEmpty() && deviceKey !in ref.devices) {
            problems.add(
                "device_key ${PyJson.repr(deviceKey)} is not in the reference set (known: " +
                    "${PyJson.reprList(ref.devices.keys.sorted())}) - either use one of those keys, or declare this " +
                    "device in the reference set first; do not invent a key at ingest time",
            )
        }
        return deviceKey
    }

    private fun checkProfileDrift(ref: ReferenceSet, deviceKey: String?, profileRun: JsonObject, problems: MutableList<String>) {
        if (deviceKey == null || deviceKey !in ref.devices || profileRun.isEmpty()) return
        driftOf(ref.devices.getValue(deviceKey).profile, profileRun)?.let { problems.add(it) }
    }

    /** The run's (build_type, compile_state) from provenance, checked against the frozen recipe. */
    private fun recipeOfRun(
        ref: ReferenceSet,
        prov: JsonObject?,
        problems: MutableList<String>,
    ): Pair<String?, String?> {
        val compileState = PyJson.strOrNull(PyJson.obj(PyJson.obj(prov, "cell"), "levels"), "compile")
        val buildType = PyJson.strOrNull(prov, "build_type")
        recipeMismatch("build_type", ref.recipe.buildType, buildType)?.let { problems.add(it) }
        recipeMismatch("compile_state", ref.recipe.compileState, compileState)?.let { problems.add(it) }
        return buildType to compileState
    }

    /** Fields the reference profile declares that the run's profile reports differently. */
    private fun driftOf(known: DeviceProfile, run: JsonObject): String? {
        val knownJson = StartupJson.encodeToJsonElement(DeviceProfile.serializer(), known).jsonObject
        val drift = knownJson.entries.filter { (f, v) -> f in run && PyJson.dumps(v) != PyJson.dumps(run.getValue(f)) }
        if (drift.isEmpty()) return null
        val shown = drift.joinToString(", ", "{", "}") { (f, v) ->
            "${PyJson.repr(f)}: (${pyValue(v)}, ${pyValue(run.getValue(f))})"
        }
        return "device profile drift vs reference set: $shown - an upgraded or replaced device must become a NEW device_key"
    }

    private fun pyValue(el: kotlinx.serialization.json.JsonElement): String =
        if (el is kotlinx.serialization.json.JsonPrimitive && el.isString) PyJson.repr(el.content) else PyJson.dumps(el)

    private fun recipeMismatch(field: String, want: String?, got: String?): String? {
        if (want.isNullOrEmpty() || got.isNullOrEmpty() || want == got) return null
        return "recipe mismatch on $field: frozen=${PyJson.repr(want)} run=${PyJson.repr(got)} - this is a different comparable series"
    }

    @Suppress("LongParameterList")
    private fun shapeAndHealthProblems(
        tally: Tally,
        ref: ReferenceSet,
        instrument: String,
        options: Options,
        problems: MutableList<String>,
        notes: MutableList<String>,
    ) {
        val traces = tally.traces
        val windows = tally.windows
        val lossy = tally.lossy
        val parseErrors = tally.parseErrors
        val signals = tally.signals
        if (lossy > 0) {
            val share = PERCENT * lossy / maxOf(1, traces)
            val tol = PyFormat.fixed(options.lossyTolerancePct, 1)
            if (share > options.lossyTolerancePct) {
                problems.add(
                    "$lossy/$traces traces (${PyFormat.fixed(share, 1)}%) lost written data " +
                        "(buffer level), above the $tol% tolerance - " +
                        "raise buffer size / use DISCARD / narrow the captured events; a " +
                        "baseline built from saturated traces will drift for tooling reasons",
                )
            } else {
                notes.add(
                    "NOTE: $lossy/$traces traces (${PyFormat.fixed(share, 1)}%) reported buffer-level loss, " +
                        "within the $tol% tolerance - their windows are kept and " +
                        "the count is recorded in trace_health",
                )
            }
        }
        if (parseErrors > 0 && signals.isEmpty()) {
            problems.add(
                "$parseErrors/$traces traces had event-parse errors and none was " +
                    "clean enough to inventory signals - window durations still stand, but " +
                    "signals_present is empty by tooling, NOT because the SDK emitted nothing",
            )
        }
        if (traces > 0 && windows.isEmpty()) {
            problems.add(
                "no '$instrument' window found in $traces trace(s) - wrong " +
                    "instrument for this SDK version, or the run did not record it",
            )
        }
        val shape = ref.recipe.runShape
        val expected = shape.expectedWindows
        when {
            windows.isEmpty() -> problems.add(
                "this run produced NO usable windows ($traces traces on disk) - " +
                    "there is nothing to record; fix the run, do not store a placeholder",
            )
            expected > 0 && windows.size < TRUNCATED_BELOW * expected -> problems.add(
                "truncated run: ${windows.size} windows against a declared shape of " +
                    "${shape.passes}x${shape.iterations}=$expected - the missing " +
                    "passes are the later, warmer ones, so this is not comparable to a full " +
                    "run; re-run it or store it with --force and a reason",
            )
            expected > 0 && windows.size > OVER_LONG_ABOVE * expected -> problems.add(
                "over-long run: ${windows.size} windows against a declared shape of " +
                    "${shape.passes}x${shape.iterations}=$expected - the run did " +
                    "NOT use the declared shape, and shape is part of the series key, so this " +
                    "is a different experiment rather than extra data; fix the harness's " +
                    "iteration count and re-run, or declare the shape this run actually used",
            )
        }
    }
}
