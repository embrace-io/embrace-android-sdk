package io.embrace.startup.analysis

import io.embrace.startup.analysis.VarianceAnalysis.Record
import io.embrace.startup.core.stats.Descriptive
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * `hypothesis_tests.py`: cross-pass evidence for one device's campaign.
 *
 * - H2 pass-level fast/slow alternation, uniform CPU inflation, block-resume sections ~2×
 * - H1 within-pass outliers = little-core placement (needs the device's real cluster map)
 * - H3 persisted-config-load bimodal: iter000 (fresh install, no cached config) fast
 * - H4 off-window fluctuators: power-service-registration binder stalls; native-lib IO
 *
 * Run per device BEFORE any cross-device or cross-arm comparison: if H2 fires, only matching-state
 * passes may be compared. Note the label/threshold mismatch carried over from the Python: the
 * "slow iterations (delta > +4 ms)" line uses an effective threshold of max(4 ms, 10% of the pass
 * median), so "slow" scales across device tiers.
 */
object HypothesisTests {

    const val SLOW_DELTA_MS: Double = 4.0
    const val CL0_MAJORITY: Double = 0.5
    const val CL0_ABSENT: Double = 0.10
    const val CFGLOAD_FAST_MS: Double = 5.0
    const val POWER_STALL_MS: Double = 10.0

    val PURE_CPU: List<String> = listOf("emb-span-service-init", "emb-otel-tracer-init")
    val BLOCK_RESUME: List<String> = listOf(
        "emb-config-service-init",
        "emb-payload-source-init",
        "emb-essential-service-init",
        "emb-post-init",
        "emb-delivery-init",
    )

    /** Battery temperatures at pass start and end, keyed by pass number, from `campaign.log`. */
    data class Temps(val start: Double?, val end: Double?)

    fun parseTemps(log: Path): Map<Int, Temps> {
        if (!Files.exists(log)) return emptyMap()
        val starts = HashMap<Int, Double>()
        val ends = HashMap<Int, Double>()
        Files.readAllLines(log).forEach { ln ->
            START.find(ln)?.let { starts[it.groupValues[1].toInt()] = it.groupValues[2].toDouble() }
            DONE.find(ln)?.let { ends[it.groupValues[1].toInt()] = it.groupValues[2].toDouble() }
        }
        return (starts.keys + ends.keys).associateWith { Temps(starts[it], ends[it]) }
    }

    fun report(
        passes: List<List<Record>>,
        temps: Map<Int, Temps> = emptyMap(),
        littleCpus: Set<Int> = VarianceAnalysis.DEFAULT_LITTLE_CPUS,
    ): String {
        val out = StringBuilder()
        out.line("hypothesis tests over ${passes.size} passes, ${passes.sumOf { it.size }} iterations total")
        out.line()
        out.h2(passes, temps)
        out.h1(passes, littleCpus)
        out.h3(passes)
        out.h4(passes)
        return out.toString()
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.h2(passes: List<List<Record>>, temps: Map<Int, Temps>) {
        line("H2. pass-level windows (ms) and battery temps")
        line(
            "  ${"pass".padStart(W4)}${"n".padStart(W5)}${"p50".padStart(W8)}${"p90".padStart(W8)}${"max".padStart(W8)}" +
                "${"run p50".padStart(W9)}${"temp start".padStart(W12)}${"temp end".padStart(W10)}",
        )
        val meds = ArrayList<Double>()
        passes.forEachIndexed { i, data ->
            val wins = data.map { it.window() }
            val runs = data.map { it.cpuMs.values.sum() }
            val med = Quantile.median(wins.sorted())
            meds.add(med)
            val t = temps[i + 1]
            line(
                "  ${(i + 1).toString().padStart(W4)}${wins.size.toString().padStart(W5)}${f(med, 1, W8)}" +
                    "${f(pctl(wins, P90), 1, W8)}${f(wins.max(), 1, W8)}${f(Quantile.median(runs.sorted()), 1, W9)}" +
                    "${fmtTemp(t?.start).padStart(W12)}${fmtTemp(t?.end).padStart(W10)}",
            )
        }
        line("  pass medians sequence: ${meds.joinToString(" -> ") { PyFormat.fixed(it, 1) }}")
        val fastest = meds.indices.minByOrNull { meds[it] } ?: 0
        val slowest = meds.indices.maxByOrNull { meds[it] } ?: 0
        line(
            "  fastest pass ${fastest + 1} (${PyFormat.fixed(meds[fastest], 1)}), " +
                "slowest pass ${slowest + 1} (${PyFormat.fixed(meds[slowest], 1)}), " +
                "swing ${PyFormat.fixed(meds[slowest] - meds[fastest], 1)} ms",
        )
        line("  slow/fast section-median ratios (H2 predicts pure-CPU ~1x, block-resume ~2x):")
        (PURE_CPU + BLOCK_RESUME).forEach { c ->
            val fast = medSection(passes[fastest], c)
            val slow = medSection(passes[slowest], c)
            val kind = if (c in PURE_CPU) "pure-CPU" else "block-resume"
            line("    ${c.padEnd(W38)}${kind.padEnd(W14)}${f(fast, 2, W7)}${f(slow, 2, W7)}  ratio ${f(slow / fast, 2, W5)}x")
        }
        line()
    }

    private fun StringBuilder.h1(passes: List<List<Record>>, littleCpus: Set<Int>) {
        line("H1. little-core placement vs window")
        val allDelta = ArrayList<Double>()
        val allShare = ArrayList<Double>()
        passes.forEachIndexed { i, data ->
            val wins = data.map { it.window() }
            val med = Quantile.median(wins.sorted())
            val shares = data.map { cl0Share(it, littleCpus) }
            val r = Descriptive.pearson(wins, shares)
            val c0 = wins.filterIndexed { j, _ -> shares[j] > CL0_MAJORITY }
            val c1 = wins.filterIndexed { j, _ -> shares[j] <= CL0_MAJORITY }
            line(
                "  pass ${i + 1}: r=${f(r, 2, W5)}  little-majority n=${c0.size.toString().padStart(W3)} " +
                    "mean=${meanOr(c0).padStart(W6)}  cluster1-majority n=${c1.size.toString().padStart(W3)} " +
                    "mean=${meanOr(c1).padStart(W6)}",
            )
            wins.forEach { allDelta.add(it - med) }
            allShare.addAll(shares)
        }
        line(
            "  pooled r(window-delta-vs-pass-median, little-share) = " +
                "${PyFormat.fixed(Descriptive.pearson(allDelta, allShare), 2)}   n=${allDelta.size}",
        )
        val slowIters = passes.flatMapIndexed { i, data ->
            val med = Quantile.median(data.map { it.window() }.sorted())
            val threshold = maxOf(SLOW_DELTA_MS, TENTH * med)
            data.mapIndexedNotNull { j, d -> if (d.window() - med > threshold) Triple(i + 1, j, d) else null }
        }
        val onC0 = slowIters.filter { cl0Share(it.third, littleCpus) > CL0_MAJORITY }
        val falsifiers = slowIters.filter { cl0Share(it.third, littleCpus) < CL0_ABSENT }
        line(
            "  slow iterations (delta > +${PyFormat.fixed(SLOW_DELTA_MS, 0)} ms): ${slowIters.size} total, " +
                "${onC0.size} little-majority, ${falsifiers.size} FALSIFIERS (<${PyFormat.percent0(CL0_ABSENT)} little-core)",
        )
        falsifiers.take(MAX_FALSIFIERS).forEach { (pn, j, d) ->
            line(
                "    pass$pn iter${j.toString().padStart(3, '0')}: window ${PyFormat.fixed(d.window(), 1)}, " +
                    "little ${PyFormat.percent0(cl0Share(d, littleCpus))}, " +
                    "cfgload ${PyFormat.fixed(d.dur["emb-persisted-config-load"] ?: 0.0, 1)}, " +
                    "spansvc ${PyFormat.fixed(d.dur["emb-span-service-init"] ?: 0.0, 1)}",
            )
        }
        line()
    }

    private fun StringBuilder.h3(passes: List<List<Record>>) {
        line("H3. persisted-config-load: iter000 (fresh install) vs the rest, ms")
        line(
            "  ${"pass".padStart(W4)}${"iter000".padStart(W9)}${"rest min".padStart(W10)}${"rest p50".padStart(W10)}" +
                "${"rest max".padStart(W10)}${"rest <5ms".padStart(W11)}",
        )
        passes.forEachIndexed { i, data ->
            val c0 = data[0].dur["emb-persisted-config-load"] ?: Double.NaN
            val rest = data.drop(1).mapNotNull { it.dur["emb-persisted-config-load"] }
            val fastRest = rest.count { it < CFGLOAD_FAST_MS }
            val sorted = rest.sorted()
            line(
                "  ${(i + 1).toString().padStart(W4)}${f(c0, 2, W9)}${f(sorted.first(), 2, W10)}" +
                    "${f(Quantile.median(sorted), 2, W10)}${f(sorted.last(), 2, W10)}${fastRest.toString().padStart(W11)}",
            )
        }
        line()
    }

    private fun StringBuilder.h4(passes: List<List<Record>>) {
        line("H4. off-window fluctuators")
        line(
            "  ${"pass".padStart(W4)}${"power p50".padStart(W10)}${"power max".padStart(W10)}${"stalls>10".padStart(W10)}" +
                "${"natlib io p50".padStart(W15)}${"sighand io p50".padStart(W15)}${"snap max".padStart(W10)}",
        )
        passes.forEachIndexed { i, data ->
            val power = data.mapNotNull { it.dur["emb-power-service-registration"] }.sorted()
            val stalls = power.count { it > POWER_STALL_MS }
            val natIo = data.map { ioMs(it, "emb-load-embrace-native-lib") }.sorted()
            val sigIo = data.map { ioMs(it, "emb-install-native-crash-signal-handlers") }.sorted()
            val snap = data.map { it.dur["emb-snapshot-session"] ?: 0.0 }
            line(
                "  ${(i + 1).toString().padStart(W4)}${f(Quantile.median(power), 2, W10)}${f(power.last(), 2, W10)}" +
                    "${stalls.toString().padStart(W10)}${f(Quantile.median(natIo), 2, W15)}" +
                    "${f(Quantile.median(sigIo), 2, W15)}${f(snap.max(), 2, W10)}",
            )
        }
    }

    fun cl0Share(d: Record, littleCpus: Set<Int>): Double {
        val tot = d.cpuMs.values.sum().takeIf { it != 0.0 } ?: 1.0
        return d.cpuMs.filterKeys { it.toInt() in littleCpus }.values.sum() / tot
    }

    fun ioMs(d: Record, section: String): Double {
        val st = d.states[section] ?: emptyMap()
        return (st["D"] ?: 0.0) + (st["D+io"] ?: 0.0) + (st["DK"] ?: 0.0) + (st["DK+io"] ?: 0.0) + (st["S+io"] ?: 0.0)
    }

    /** `pctl`: nearest-rank on `round(p/100 * (n-1))`, Python's half-even `round`. Kept for this report only. */
    fun pctl(vals: List<Double>, p: Int): Double {
        val s = vals.sorted()
        val idx = Math.rint(p / PERCENT * (s.size - 1)).toInt()
        return s[minOf(s.size - 1, idx)]
    }

    private fun medSection(data: List<Record>, c: String): Double =
        Quantile.median(data.mapNotNull { it.dur[c] }.sorted())

    private fun meanOr(vals: List<Double>): String =
        if (vals.isEmpty()) "--" else PyFormat.fixed(PyFormat.exactMean(vals), 1)

    private fun fmtTemp(t: Double?): String = if (t == null) "--" else "${PyFormat.fixed(t, 1)}C"

    private fun f(x: Double, decimals: Int, width: Int) = PyFormat.fixed(x, decimals).padStart(width)

    private fun Record.window(): Double = requireNotNull(windowMs) { "$trace has no window_ms" }

    private val START = Regex("pass (\\d+)/\\d+ starting, battery ([\\d.]+)")
    private val DONE = Regex("pass (\\d+) done in .* battery ([\\d.]+)")
    private const val P90 = 90
    private const val TENTH = 0.10
    private const val MAX_FALSIFIERS = 12
    private const val PERCENT = 100.0
    private const val W3 = 3
    private const val W4 = 4
    private const val W5 = 5
    private const val W6 = 6
    private const val W7 = 7
    private const val W8 = 8
    private const val W9 = 9
    private const val W10 = 10
    private const val W11 = 11
    private const val W12 = 12
    private const val W14 = 14
    private const val W15 = 15
    private const val W38 = 38
}
