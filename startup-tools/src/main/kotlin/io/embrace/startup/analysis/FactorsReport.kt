package io.embrace.startup.analysis

import io.embrace.startup.analysis.OutlierFactors.Record
import io.embrace.startup.core.stats.Descriptive
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat

/**
 * `factors_report.py`: correlate external/on-device factors with SDK-init window slowness,
 * outlier-first - pass-level factor means, pooled per-iteration correlations against the window
 * delta, the outlier catalogue, extreme-outlier detail and D-state blocked-function totals.
 *
 * `outlier_metrics.sql` partitions run time at a fixed cpu<4 boundary; [cl0Share] remaps that
 * partition against the device's real little cluster, exactly as the Python did.
 */
object FactorsReport {

    const val SLOW_DELTA_MS: Double = 4.0

    /** The Python's per-iteration `derive()` row. */
    data class Row(
        val pass: Int,
        val iter: Int,
        val window: Double,
        val delta: Double,
        val run: Double,
        val dIo: Double,
        val rq: Double,
        val blocked: Map<String, Double>,
        val effMhz: Double?,
        val limCl1: Double?,
        val cl0Share: Double,
        val sysserverRate: Double,
        val sfRate: Double,
        val otherRate: Double,
        val inprocRate: Double,
        val artload: Double,
        val artverify: Double,
        val lock: Double,
        val gc: Double,
        val binder: Double,
        val swap: Double,
        val memavail: Double,
        val othercpu: Map<String, Double>,
    )

    fun derive(rec: Record, pass: Int, iter: Int, passMedian: Double, littleCpus: Set<Int>): Row {
        val st = rec.states
        val win = requireNotNull(rec.windowMs) { "${rec.trace} has no window_ms" }
        val oth = rec.othercpu
        return Row(
            pass = pass,
            iter = iter,
            window = win,
            delta = win - passMedian,
            run = st["Running"] ?: 0.0,
            dIo = st.filterKeys { it.startsWith("D") || it.startsWith("S+io") }.values.sum(),
            rq = st.filterKeys { it.substringBefore(":") == "R" || it.substringBefore(":") == "R+" }.values.sum(),
            blocked = blockedFunctions(st),
            effMhz = rec.effMhz,
            limCl1 = rec.freqLimitCl1,
            cl0Share = cl0Share(rec, littleCpus),
            sysserverRate = (oth["system_server"] ?: 0.0) / win,
            sfRate = (oth["/system/bin/surfaceflinger"] ?: 0.0) / win,
            otherRate = oth.filterKeys { it != "swapper" }.values.sum() / win,
            inprocRate = rec.inproc.values.sum() / win,
            artload = rec.artClassloadMs ?: 0.0,
            artverify = rec.artVerifyMs ?: 0.0,
            lock = rec.lockContentionMs ?: 0.0,
            gc = rec.gcSliceMs ?: 0.0,
            binder = rec.binderTxnCnt ?: 0.0,
            swap = rec.memSwap ?: 0.0,
            memavail = rec.memAvailable ?: 0.0,
            othercpu = oth,
        )
    }

    /** D-state time by `blocked_function`, summed over the io/non-io variants of each state key. */
    private fun blockedFunctions(states: Map<String, Double>): Map<String, Double> {
        val blocked = LinkedHashMap<String, Double>()
        states.forEach { (k, v) ->
            if (k.startsWith("D") && ":" in k) {
                val fn = k.substringAfter(":")
                blocked[fn] = (blocked[fn] ?: 0.0) + v
            }
        }
        return blocked
    }

    fun cl0Share(rec: Record, littleCpus: Set<Int>): Double {
        val rCl0 = rec.runCl0Ms ?: 0.0
        val rCl1 = rec.runCl1Ms ?: 0.0
        val tot = rCl0 + rCl1
        if (tot == 0.0) return 0.0
        return if (littleCpus.all { cpu -> cpu >= FIXED_SPLIT }) rCl1 / tot else rCl0 / tot
    }

    fun rows(passes: List<List<Record>>, littleCpus: Set<Int>): List<Row> =
        passes.flatMapIndexed { pi, data ->
            val med = Quantile.median(data.map { requireNotNull(it.windowMs) }.sorted())
            data.mapIndexed { j, rec -> derive(rec, pi + 1, j, med, littleCpus) }
        }

    fun report(passes: List<List<Record>>, littleCpus: Set<Int> = VarianceAnalysis.DEFAULT_LITTLE_CPUS): String {
        val rows = rows(passes, littleCpus)
        val out = StringBuilder()
        out.line("external-factor analysis: ${passes.size} passes, ${rows.size} iterations")
        out.line()
        out.passMeans(rows, passes.size)
        out.correlations(rows)
        out.catalogue(rows)
        out.extremes(rows)
        out.blockedTotals(rows)
        return out.toString()
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.passMeans(rows: List<Row>, passCount: Int) {
        line("1. pass-level factor means (rates are CPU-ms per window-ms)")
        line(
            "  ${"pass".padStart(W4)}${"win p50".padStart(W9)}${"run p50".padStart(W9)}${"eff_mhz".padStart(W9)}" +
                "${"lim_cl1".padStart(W9)}${"sysserv rate".padStart(W13)}${"sf rate".padStart(W9)}" +
                "${"other rate".padStart(W11)}${"artload".padStart(W9)}${"d_io".padStart(W7)}${"swap MB".padStart(W9)}",
        )
        for (pi in 1..passCount) {
            val pr = rows.filter { it.pass == pi }
            line(
                "  ${pi.toString().padStart(W4)}" +
                    f(median(pr.map { it.window }), 1, W9) +
                    f(median(pr.map { it.run }), 1, W9) +
                    f(mean0(pr.mapNotNull { it.effMhz }.filter { it != 0.0 }), 0, W9) +
                    f(mean0(pr.mapNotNull { it.limCl1 }.filter { it != 0.0 }), 0, W9) +
                    f(PyFormat.exactMean(pr.map { it.sysserverRate }), 3, W13) +
                    f(PyFormat.exactMean(pr.map { it.sfRate }), 3, W9) +
                    f(PyFormat.exactMean(pr.map { it.otherRate }), 3, W11) +
                    f(PyFormat.exactMean(pr.map { it.artload }), 2, W9) +
                    f(PyFormat.exactMean(pr.map { it.dIo }), 2, W7) +
                    f(PyFormat.exactMean(pr.map { it.swap }) / MEGA, 1, W9),
            )
        }
        line()
    }

    private fun StringBuilder.correlations(rows: List<Row>) {
        line("2. pooled correlations with window delta (vs own pass median), n=${rows.size}")
        val deltas = rows.map { it.delta }
        val factors: List<Pair<String, (Row) -> Double>> = listOf(
            "eff_mhz (weighted CPU clock)" to { r -> r.effMhz ?: 0.0 },
            "little share" to { r -> r.cl0Share },
            "system_server CPU rate" to { r -> r.sysserverRate },
            "surfaceflinger CPU rate" to { r -> r.sfRate },
            "other-proc CPU rate (excl idle)" to { r -> r.otherRate },
            "in-proc bg-thread CPU rate" to { r -> r.inprocRate },
            "main-thread D/io ms" to { r -> r.dIo },
            "main-thread runnable ms" to { r -> r.rq },
            "ART class-load ms (in window)" to { r -> r.artload },
            "ART verify ms (in window)" to { r -> r.artverify },
            "lock contention ms" to { r -> r.lock },
            "GC slice ms (in proc)" to { r -> r.gc },
            "binder txns (main thread)" to { r -> r.binder },
            "process swap bytes" to { r -> r.swap },
            "MemAvailable" to { r -> r.memavail },
        )
        factors.forEach { (label, get) ->
            line("  ${label.padEnd(W36)} r = ${f(Descriptive.pearson(deltas, rows.map(get)), 2, W6)}")
        }
        line("  ${"(sanity) run ms".padEnd(W36)} r = ${f(Descriptive.pearson(deltas, rows.map { it.run }), 2, W6)}")
        line()
    }

    private fun StringBuilder.catalogue(rows: List<Row>) {
        val slows = rows.filter { it.delta > SLOW_DELTA_MS }.sortedByDescending { it.delta }
        line("3. outlier catalogue — ${slows.size} iterations with delta > +${PyFormat.fixed(SLOW_DELTA_MS, 0)} ms")
        line(
            "  ${"iter".padStart(W13)}${"win".padStart(W7)}${"Δ".padStart(W6)}${"run".padStart(W7)}${"MHz".padStart(W6)}" +
                "${"lit%".padStart(W6)}${"ss rate".padStart(W8)}${"sf rate".padStart(W8)}${"oth".padStart(W6)}" +
                "${"d_io".padStart(W6)}${"rq".padStart(W5)}${"artload".padStart(W8)}${"verify".padStart(W7)}" +
                "${"lock".padStart(W6)}${"gc".padStart(W5)}",
        )
        slows.forEach { r ->
            line(
                "  pass${r.pass}:it${iter3(r.iter)}${f(r.window, 1, W7)}${PyFormat.fixedSigned(r.delta, 1).padStart(W6)}" +
                    "${f(r.run, 1, W7)}${f(r.effMhz ?: 0.0, 0, W6)}${f(r.cl0Share * PERCENT, 0, W6)}" +
                    "${f(r.sysserverRate, 2, W8)}${f(r.sfRate, 2, W8)}${f(r.otherRate, 2, W6)}${f(r.dIo, 2, W6)}" +
                    "${f(r.rq, 1, W5)}${f(r.artload, 2, W8)}${f(r.artverify, 2, W7)}${f(r.lock, 2, W6)}${f(r.gc, 2, W5)}",
            )
        }
        val medFast = rows.filter { kotlin.math.abs(it.delta) < BASELINE_BAND }
        line(
            "  --- baseline (|Δ|<2 ms, n=${medFast.size}): " +
                "run ${PyFormat.fixed(median(medFast.map { it.run }), 1)}, " +
                "ss rate ${PyFormat.fixed(meanOrNan(medFast.map { it.sysserverRate }), 2)}, " +
                "sf rate ${PyFormat.fixed(meanOrNan(medFast.map { it.sfRate }), 2)}, " +
                "oth ${PyFormat.fixed(meanOrNan(medFast.map { it.otherRate }), 2)}, " +
                "d_io ${PyFormat.fixed(median(medFast.map { it.dIo }), 2)}, " +
                "artload ${PyFormat.fixed(median(medFast.map { it.artload }), 2)}, " +
                "verify ${PyFormat.fixed(median(medFast.map { it.artverify }), 2)}",
        )
        line()
    }

    private fun StringBuilder.extremes(rows: List<Row>) {
        line("4. extreme outliers — top competitors and blocked functions")
        rows.sortedByDescending { it.window }.take(TOP_EXTREMES).forEach { r ->
            val topOth = r.othercpu.entries.sortedByDescending { it.value }.take(TOP_COMPETITORS)
            val topBlk = r.blocked.entries.sortedByDescending { it.value }.take(TOP_BLOCKED)
            val oth = topOth.joinToString(", ") { "${it.key.substringAfterLast('/')} ${PyFormat.fixed(it.value, 1)}" }
            val blk = topBlk.joinToString(", ") { "${it.key.ifEmpty { "?" }} ${PyFormat.fixed(it.value, 2)}" }.ifEmpty { "none" }
            line(
                "  pass${r.pass}:it${iter3(r.iter)} win ${PyFormat.fixed(r.window, 1)} " +
                    "(Δ${PyFormat.fixedSigned(r.delta, 1)})  competitors[ms]: $oth",
            )
            line("       blocked_on: $blk")
        }
        line()
    }

    private fun StringBuilder.blockedTotals(rows: List<Row>) {
        line("5. main-thread D-state blocked_function totals across all iterations, ms")
        val agg = LinkedHashMap<String, Double>()
        rows.forEach { r -> r.blocked.forEach { (k, v) -> agg[k.ifEmpty { "?" }] = (agg[k.ifEmpty { "?" }] ?: 0.0) + v } }
        agg.entries.sortedByDescending { it.value }.take(TOP_BLOCKED_TOTALS).forEach { (k, v) ->
            line("  ${k.padEnd(W44)}${f(v, 1, W9)}")
        }
    }

    /** `statistics.median`, or NaN on an empty list where the Python would have raised. */
    private fun median(values: List<Double>): Double =
        if (values.isEmpty()) Double.NaN else Quantile.median(values.sorted())

    private fun meanOrNan(values: List<Double>): Double =
        if (values.isEmpty()) Double.NaN else PyFormat.exactMean(values)

    private fun mean0(values: List<Double>): Double = if (values.isEmpty()) 0.0 else PyFormat.exactMean(values)

    private fun iter3(i: Int) = i.toString().padStart(3, '0')

    private fun f(x: Double, decimals: Int, width: Int) = PyFormat.fixed(x, decimals).padStart(width)

    private const val FIXED_SPLIT = 4
    private const val MEGA = 1e6
    private const val PERCENT = 100.0
    private const val BASELINE_BAND = 2.0
    private const val TOP_EXTREMES = 8
    private const val TOP_COMPETITORS = 4
    private const val TOP_BLOCKED = 3
    private const val TOP_BLOCKED_TOTALS = 15
    private const val W4 = 4
    private const val W5 = 5
    private const val W6 = 6
    private const val W7 = 7
    private const val W8 = 8
    private const val W9 = 9
    private const val W11 = 11
    private const val W13 = 13
    private const val W36 = 36
    private const val W44 = 44
}
