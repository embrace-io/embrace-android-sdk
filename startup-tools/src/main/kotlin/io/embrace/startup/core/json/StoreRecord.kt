package io.embrace.startup.core.json

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One line of the longitudinal store (`store.jsonl` / `sweep-store.jsonl`), matching the existing
 * records exactly. This schema is in the PRESERVE class of the fidelity policy: existing
 * records must decode without loss and re-encode to an equivalent tree, because every published
 * version table was derived from them.
 *
 * Field notes, each earned:
 * - [windowsMs] holds every launch's composed window in run order. The store keeps per-launch values
 *   rather than aggregates precisely so that a statistic nobody has asked for yet can still be
 *   computed later; two campaigns that kept only medians made p90/p95 unanswerable.
 * - [recipe] is the comparability key. Run shape is part of it because pass count changes WHAT is
 *   measured, not just how precisely; records with different recipes are never compared.
 * - [baselineEligible] is false for snapshot/local/dirty builds, which are excluded from baselines.
 * - [derived] duplicates what [windowsMs] implies, using the index-based quantile stored records use;
 *   see [Derived] for why that definition is kept alongside the canonical one.
 */
@Serializable
data class StoreRecord(
    @SerialName("run_id") val runId: String,
    @SerialName("ingested_at") val ingestedAt: String,
    @SerialName("measured_at") val measuredAt: String? = null,
    @SerialName("device_key") val deviceKey: String,
    @SerialName("device_profile") val deviceProfile: DeviceProfile,
    /**
     * Nullable only to read history: three of the four 8.3.0 sweep records were written with
     * `sdk_version: null` (and null `build_type`/`compile_state`), salvaged from an earlier store.
     * Downstream consumers fell back to the version in [runId] for these rows, so published tables
     * were unaffected.
     * The port's ingest refuses to WRITE a null version; this field is nullable so it can still READ one.
     */
    @SerialName("sdk_version") val sdkVersion: String? = null,
    @SerialName("app_build_id") val appBuildId: String? = null,
    val recipe: Recipe,
    /** Free-form factor levels, e.g. `{"compile": "profile"}`; heterogeneous by design. */
    val conditions: JsonObject = JsonObject(emptyMap()),
    @SerialName("baseline_eligible") val baselineEligible: Boolean,
    @SerialName("signals_present") val signalsPresent: List<String> = emptyList(),
    @SerialName("trace_health") val traceHealth: TraceHealth? = null,
    @SerialName("windows_ms") val windowsMs: List<Double>,
    val derived: Derived,
    @SerialName("source_skill") val sourceSkill: String? = null,
    val notes: String = "",
)

/**
 * Stable identity of a reference device; compared field by field at ingest to detect drift.
 *
 * Every field is nullable with a null default for one reason: the three salvaged 8.3.0 sweep records
 * carry an EMPTY profile (`{}`), and a reader that cannot decode them cannot read the store. A
 * complete profile is required at ingest time; [isComplete] is the check. Fields are
 * `@EncodeDefault(NEVER)` so a run with no provenance stores `{}`, not seven nulls
 * (port log #29).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeviceProfile(
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("api_level") val apiLevel: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val release: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val vendor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("soc_family") val socFamily: String? = null,
    /** Per-cluster maximum CPU frequency in kHz, ascending. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val clusters: List<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("ram_class") val ramClass: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("storage_class") val storageClass: String? = null,
) {
    val isComplete: Boolean
        get() = apiLevel != null && release != null && vendor != null && socFamily != null &&
            clusters != null && ramClass != null && storageClass != null
}

/**
 * The comparability key. Records are only ever compared within an identical recipe; changing any
 * field starts a new series. [instrument] is nullable because the reference set deliberately ships
 * it unset so that the first ingest fails loudly rather than defaulting to a span the harness never
 * emits (which happened once, and refused every trace of a 200-launch leg).
 */
@Serializable
data class Recipe(
    /** Nullable to read the three salvaged sweep records (see [StoreRecord.sdkVersion]); never written null. */
    @SerialName("build_type") val buildType: String? = null,
    @SerialName("compile_state") val compileState: String? = null,
    val instrument: String? = null,
    @SerialName("run_shape") val runShape: RunShape,
    @SerialName("trace_processor_version") val traceProcessorVersion: String? = null,
    @SerialName("trace_processor_path") val traceProcessorPath: String? = null,
)

@Serializable
data class RunShape(val passes: Int, val iterations: Int) {
    /** The number of windows a complete run of this shape yields; ingest refuses runs outside ±10%. */
    val expectedWindows: Int get() = passes * iterations
}

@Serializable
data class TraceHealth(
    val traces: Int,
    @SerialName("buffer_loss") val bufferLoss: Int,
    @SerialName("parse_errors") val parseErrors: Int,
    @SerialName("signals_from_clean_trace") val signalsFromCleanTrace: Boolean,
)

/**
 * Aggregates as recorded on disk. Two producers exist: the per-run ingest writes
 * `n, median, p90, p95, max, iqr`; the matrix report summary writes `n, median, p90, max, iqr,
 * pass_medians` (a `pass1 → median` object). Both use the INDEX-BASED quantile
 * `values[min(n-1, floor(p*n))]`, not the Type-7 interpolation the statistics library uses - the port
 * keeps this definition for stored fields under the name "legacy index" so existing records remain
 * reproducible, and labels it wherever shown. Each producer writes only its own keys, so the two
 * producer-specific fields are never written as `null` (the side-by-side ingest caught
 * a spurious `"pass_medians": null` in the Kotlin record).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Derived(
    val n: Int,
    val median: Double,
    val p90: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val p95: Double? = null,
    val max: Double,
    val iqr: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pass_medians") val passMedians: Map<String, Double>? = null,
)
