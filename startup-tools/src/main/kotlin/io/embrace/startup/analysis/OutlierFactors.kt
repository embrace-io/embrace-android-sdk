package io.embrace.startup.analysis

import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceProcessor
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * `outlier-factors`: the external-factor catalogue (`outlier_metrics.sql`) per trace - the
 * `passN-factors.json` that `factors-report` and `hypothesis-tests` consume.
 *
 * Every scalar is optional because the query only returns rows for the fields it found non-NULL:
 * `eff_mhz` is absent when no cpufreq counter covered the window, `mem_swap` when the process had
 * no swap counter, the `freq_limit_*` pair on kernels that do not export the limit tracks. Downstream
 * code must keep treating absence as "not exported", never as zero.
 */
object OutlierFactors {

    /**
     * Every scalar is `@EncodeDefault(NEVER)`: a scalar the query returned no row for is ABSENT from the
     * dataset, exactly as the goldens have it, never `null`. Downstream readers treat the two alike, but a
     * `"freq_cl0_mhz": null` on disk would read as "the kernel exported nothing" where the truth is
     * "no counter covered the window", and the pre-cutover shadow run found such nulls on most iterations
     * of one device (port log #28).
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Record(
        val trace: String,
        /** Thread-state ms keyed `<state>[+io][:blocked_function]`, summed over duplicate keys. */
        val states: Map<String, Double>,
        /** Other threads of the app process: ms on-CPU inside the window, by thread name. */
        val inproc: Map<String, Double>,
        /** Other processes: ms on-CPU inside the window, by process (or thread) name. */
        val othercpu: Map<String, Double>,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("window_ms") val windowMs: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("eff_mhz") val effMhz: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("run_cl0_ms") val runCl0Ms: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("run_cl1_ms") val runCl1Ms: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("freq_cl0_mhz") val freqCl0Mhz: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("freq_cl1_mhz") val freqCl1Mhz: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("art_verify_ms") val artVerifyMs: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("art_classload_ms") val artClassloadMs: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("lock_contention_ms") val lockContentionMs: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("binder_txn_cnt") val binderTxnCnt: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("gc_slice_ms") val gcSliceMs: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("freq_limit_cl0") val freqLimitCl0: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("freq_limit_cl1") val freqLimitCl1: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mem_swap") val memSwap: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mem_available") val memAvailable: Double? = null,
    )

    /** Every scalar `what` the query can emit, in the order the SQL declares them. */
    val SCALARS: List<String> = listOf(
        "window_ms", "eff_mhz", "run_cl0_ms", "run_cl1_ms", "freq_cl0_mhz", "freq_cl1_mhz", "art_verify_ms",
        "art_classload_ms", "lock_contention_ms", "binder_txn_cnt", "gc_slice_ms", "freq_limit_cl0", "freq_limit_cl1",
        "mem_swap", "mem_available",
    )

    fun recordOf(trace: String, triples: List<TraceProcessor.Triple>): Record {
        val states = LinkedHashMap<String, Double>()
        val inproc = LinkedHashMap<String, Double>()
        val othercpu = LinkedHashMap<String, Double>()
        val scalars = HashMap<String, Double>()
        triples.forEach { t ->
            val value = t.value?.toDouble() ?: return@forEach
            when {
                t.what.startsWith("state:") -> {
                    val key = t.what.removePrefix("state:") + (if (t.k.isNotEmpty()) ":${t.k}" else "")
                    states[key] = (states[key] ?: 0.0) + value
                }
                t.what == "inproc" -> inproc[t.k] = value
                t.what == "othercpu" -> othercpu[t.k] = value
                else -> scalars[t.what] = value
            }
        }
        val unknown = scalars.keys - SCALARS.toSet()
        require(unknown.isEmpty()) { "outlier_metrics.sql emitted rows this port does not model: $unknown" }
        return Record(
            trace = trace,
            states = states,
            inproc = inproc,
            othercpu = othercpu,
            windowMs = scalars["window_ms"],
            effMhz = scalars["eff_mhz"],
            runCl0Ms = scalars["run_cl0_ms"],
            runCl1Ms = scalars["run_cl1_ms"],
            freqCl0Mhz = scalars["freq_cl0_mhz"],
            freqCl1Mhz = scalars["freq_cl1_mhz"],
            artVerifyMs = scalars["art_verify_ms"],
            artClassloadMs = scalars["art_classload_ms"],
            lockContentionMs = scalars["lock_contention_ms"],
            binderTxnCnt = scalars["binder_txn_cnt"],
            gcSliceMs = scalars["gc_slice_ms"],
            freqLimitCl0 = scalars["freq_limit_cl0"],
            freqLimitCl1 = scalars["freq_limit_cl1"],
            memSwap = scalars["mem_swap"],
            memAvailable = scalars["mem_available"],
        )
    }

    fun extract(tp: TraceProcessor, trace: Path): Record =
        recordOf(trace.fileName.toString(), tp.triples(Queries.OUTLIER_METRICS, trace))
}
