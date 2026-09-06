package io.embrace.startup.analysis

import io.embrace.startup.core.stats.Descriptive
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceProcessor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * `variance_analysis.py`: per-iteration variance over a directory of traces - the iteration matrix
 * (A), per-section fluctuation and correlation with the window (B), outlier decomposition of the
 * four slowest iterations (C), the thread-state split inside each section (D) and the main thread's
 * per-CPU residency with the little-cluster share (E).
 *
 * The `--json` dataset is the input contract of `hypothesis-tests`, `factors-report` and
 * `cross-device-sections`; [Record] is that schema.
 */
object VarianceAnalysis {

    val CANONICAL: List<String> = listOf(
        "emb-embrace-impl-init", "emb-bootstrapper-init", "emb-modules-init", "emb-persisted-config-load",
        "emb-config-service-init", "emb-span-service-init", "emb-otel-tracer-init", "emb-essential-service-init",
        "emb-delivery-init", "emb-payload-source-init", "emb-post-init", "emb-post-services-setup",
        "emb-load-instrumentation",
    )
    val EXTRAS: List<String> = listOf(
        "emb-install-native-crash-signal-handlers",
        "emb-load-embrace-native-lib",
        "emb-record-startup",
        "emb-power-service-registration",
        "emb-snapshot-session",
    )
    val SHORT: Map<String, String> = mapOf(
        "emb-embrace-impl-init" to "impl",
        "emb-bootstrapper-init" to "boot",
        "emb-modules-init" to "modules",
        "emb-persisted-config-load" to "cfgload",
        "emb-config-service-init" to "cfgsvc",
        "emb-span-service-init" to "spansvc",
        "emb-otel-tracer-init" to "tracer",
        "emb-essential-service-init" to "essent",
        "emb-delivery-init" to "deliv",
        "emb-payload-source-init" to "payload",
        "emb-post-init" to "postini",
        "emb-post-services-setup" to "postsvc",
        "emb-load-instrumentation" to "loadins",
        "emb-install-native-crash-signal-handlers" to "sighand",
        "emb-load-embrace-native-lib" to "natlib",
        "emb-record-startup" to "recstart",
        "emb-power-service-registration" to "power",
        "emb-snapshot-session" to "snap",
    )
    val DEFAULT_LITTLE_CPUS: Set<Int> = setOf(0, 1, 2, 3)

    /** One trace's dataset - the `--json` element schema, key names as the Python wrote them. */
    @Serializable
    data class Record(
        val trace: String,
        /** Absent when the trace has no `emb-sdk-start` slice; the Python then failed at report time. */
        @SerialName("window_ms") val windowMs: Double? = null,
        /** First-occurrence duration of every `emb-*` section. */
        val dur: Map<String, Double>,
        /** Main-thread residency per CPU (string keys, as JSON has them). */
        @SerialName("cpu_ms") val cpuMs: Map<String, Double>,
        /** Per section, per thread state (`Running`, `S`, `D+io`, ...) ms of the emitting thread. */
        val states: Map<String, Map<String, Double>>,
    )

    fun recordOf(trace: String, triples: List<TraceProcessor.Triple>): Record {
        var window: Double? = null
        val dur = LinkedHashMap<String, Double>()
        val cpu = LinkedHashMap<String, Double>()
        val states = LinkedHashMap<String, LinkedHashMap<String, Double>>()
        triples.forEach { t ->
            val value = t.value?.toDouble() ?: return@forEach
            when {
                t.what == "window_ms" -> window = value
                t.what == "cpu_ms" -> cpu[t.k] = value
                t.what == "dur" -> dur[t.k] = value
                t.what.startsWith("st:") -> states.getOrPut(t.k) { LinkedHashMap() }[t.what.removePrefix("st:")] = value
            }
        }
        return Record(trace, window, dur, cpu, states)
    }

    fun extract(tp: TraceProcessor, trace: Path): Record =
        recordOf(trace.fileName.toString(), tp.triples(Queries.VARIANCE_METRICS, trace))

    fun report(data: List<Record>, littleCpus: Set<Int> = DEFAULT_LITTLE_CPUS): String {
        val out = StringBuilder()
        val windows = data.map { requireNotNull(it.windowMs) { "${it.trace} has no emb-sdk-start window" } }
        val cols = CANONICAL + EXTRAS
        out.matrix(data, windows, cols)
        out.fluctuation(data, windows, cols)
        val slow = windows.indices.sortedByDescending { windows[it] }.take(SLOWEST)
        out.decomposition(data, windows, cols, slow)
        out.threadStates(data, cols, slow)
        out.residency(data, windows, littleCpus)
        return out.toString()
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.matrix(data: List<Record>, windows: List<Double>, cols: List<String>) {
        line("A. per-iteration durations, ms")
        line("  it   window " + cols.joinToString(" ") { SHORT.getValue(it).padStart(W8) })
        data.forEachIndexed { i, m ->
            val row = cols.joinToString(" ") { PyFormat.fixed(m.dur[it] ?: Double.NaN, 2).padStart(W8) }
            line("  ${i.toString().padStart(W3)} ${PyFormat.fixed(windows[i], 1).padStart(W8)} $row")
        }
        line()
    }

    private fun StringBuilder.fluctuation(data: List<Record>, windows: List<Double>, cols: List<String>) {
        line("B. per-section fluctuation (n=${data.size}), ms")
        line(
            "  ${"section".padEnd(W42)}${"min".padStart(W7)}${"med".padStart(W7)}${"max".padStart(W7)}" +
                "${"spread".padStart(W8)}${"stdev".padStart(W7)}${"r_win".padStart(W7)}",
        )
        cols.forEach { c ->
            val vals = data.mapNotNull { it.dur[c] }
            if (vals.size < data.size) return@forEach
            val sorted = vals.sorted()
            line(
                "  ${c.padEnd(W42)}${f2(sorted.first(), W7)}${f2(Quantile.median(sorted), W7)}${f2(sorted.last(), W7)}" +
                    "${f2(sorted.last() - sorted.first(), W8)}${f2(Descriptive.sampleStdev(vals), W7)}" +
                    f2(Descriptive.pearson(vals, windows), W7),
            )
        }
        line()
    }

    private fun StringBuilder.decomposition(data: List<Record>, windows: List<Double>, cols: List<String>, slow: List<Int>) {
        val wmed = Quantile.median(windows.sorted())
        // The Python's statistics.median raised on a section present in NO trace; NaN here keeps the
        // report renderable and the "> 0.3" filter drops it, which is the only sane reading.
        val med = cols.associateWith { c ->
            val vals = data.mapNotNull { it.dur[c] }
            if (vals.isEmpty()) Double.NaN else Quantile.median(vals.sorted())
        }
        line("C. slowest iterations: section excess vs median (window Δ = window - median window)")
        slow.forEach { i ->
            val m = data[i]
            val deltas = cols.map { c -> c to ((m.dur[c] ?: 0.0) - med.getValue(c)) }
                .sortedByDescending { it.second }
                .take(TOP_DELTAS)
            val ds = deltas.filter { it.second > DELTA_FLOOR }
                .joinToString(", ") { (c, d) -> "${SHORT.getValue(c)} +${PyFormat.fixed(d, 1)}" }
            val iter = i.toString().padStart(ITER_DIGITS, '0')
            line("  iter$iter  window ${PyFormat.fixed(windows[i], 1)} (Δ +${PyFormat.fixed(windows[i] - wmed, 1)}): $ds")
        }
        line()
    }

    private fun StringBuilder.threadStates(data: List<Record>, cols: List<String>, slow: List<Int>) {
        line("D. thread-state inside each section, ms (median across iters | mean over 4 slowest iters)")
        line(
            "  ${"section".padEnd(W42)}${"run".padStart(W7)}${"sleep".padStart(W7)}${"io/D".padStart(W7)}${"rq".padStart(W6)}  |" +
                "${"run".padStart(W7)}${"sleep".padStart(W7)}${"io/D".padStart(W7)}${"rq".padStart(W6)}",
        )
        cols.forEach { c ->
            val medSplit = splitStats(data, c, data.indices.toList()) ?: return@forEach
            val slowSplit = splitStats(data, c, slow) ?: return@forEach
            line("  ${c.padEnd(W42)}${splitCells(medSplit)}  |${splitCells(slowSplit)}")
        }
        line()
    }

    private fun StringBuilder.residency(data: List<Record>, windows: List<Double>, littleCpus: Set<Int>) {
        val cpus = data.flatMap { m -> m.cpuMs.keys.map { it.toInt() } }.toSortedSet().toList()
        line("E. main-thread CPU residency inside window, ms per cpu")
        line("  it   window " + cpus.joinToString(" ") { "cpu${it.toString().padStart(W4)}" } + "   little-share")
        val shares = ArrayList<Double>()
        data.forEachIndexed { i, m ->
            val tot = m.cpuMs.values.sum().takeIf { it != 0.0 } ?: 1.0
            val cl0 = m.cpuMs.filterKeys { it.toInt() in littleCpus }.values.sum() / tot
            shares.add(cl0)
            val row = cpus.joinToString(" ") { PyFormat.fixed(m.cpuMs[it.toString()] ?: 0.0, 1).padStart(W7) }
            line(
                "  ${i.toString().padStart(
                    W3,
                )} ${PyFormat.fixed(windows[i], 1).padStart(W8)} $row   ${PyFormat.fixed(cl0 * PERCENT, 1).padStart(W5)}%",
            )
        }
        line("  r(window, little-share) = ${PyFormat.fixed(Descriptive.pearson(windows, shares), 2)}")
        val cl0Wins = windows.filterIndexed { i, _ -> shares[i] > HALF }
        val cl1Wins = windows.filterIndexed { i, _ -> shares[i] <= HALF }
        if (cl0Wins.isNotEmpty() && cl1Wins.isNotEmpty()) {
            line(
                "  little-majority: n=${cl0Wins.size} mean=${PyFormat.fixed(PyFormat.exactMean(cl0Wins), 1)}" +
                    "  |  cluster1-majority: n=${cl1Wins.size} mean=${PyFormat.fixed(PyFormat.exactMean(cl1Wins), 1)}",
            )
        }
    }

    /** `split_stats`: (run, sleep, io/D, runqueue) per section over the given iterations; null if any lacks the section. */
    private fun splitStats(data: List<Record>, section: String, idxs: List<Int>): List<Double>? {
        val runs = ArrayList<Double>()
        val sleeps = ArrayList<Double>()
        val ios = ArrayList<Double>()
        val rqs = ArrayList<Double>()
        idxs.forEach { i ->
            val m = data[i]
            if (section !in m.dur) return null
            val st = m.states[section] ?: emptyMap()
            runs.add(st["Running"] ?: 0.0)
            sleeps.add(st["S"] ?: 0.0)
            ios.add(
                (st["D"] ?: 0.0) + (st["D+io"] ?: 0.0) + (st["DK"] ?: 0.0) + (st["DK+io"] ?: 0.0) + (st["S+io"] ?: 0.0),
            )
            rqs.add((st["R"] ?: 0.0) + (st["R+"] ?: 0.0))
        }
        val agg: (List<Double>) -> Double = if (idxs.size > MEDIAN_ABOVE) {
            { Quantile.median(it.sorted()) }
        } else {
            { PyFormat.exactMean(it) }
        }
        return listOf(agg(runs), agg(sleeps), agg(ios), agg(rqs))
    }

    private fun splitCells(split: List<Double>): String =
        split.take(3).joinToString("") { f2(it, W7) } + f2(split[3], W6)

    private fun f2(x: Double, width: Int) = PyFormat.fixed(x, 2).padStart(width)

    private const val SLOWEST = 4
    private const val ITER_DIGITS = 3
    private const val TOP_DELTAS = 7
    private const val DELTA_FLOOR = 0.3
    private const val MEDIAN_ABOVE = 6
    private const val HALF = 0.5
    private const val PERCENT = 100.0
    private const val W3 = 3
    private const val W4 = 4
    private const val W5 = 5
    private const val W6 = 6
    private const val W7 = 7
    private const val W8 = 8
    private const val W42 = 42
}
