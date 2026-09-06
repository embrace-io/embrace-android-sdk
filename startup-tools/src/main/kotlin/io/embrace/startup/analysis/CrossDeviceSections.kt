package io.embrace.startup.analysis

import io.embrace.startup.analysis.VarianceAnalysis.Record
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat

/**
 * `cross_device_sections.py`: side-by-side per-section median / max / % of window across devices,
 * pooled over every available pass. The workload-identity check: compare section SHARES, not
 * absolute ms - matching shares mean the SDK does identical work everywhere and the differences are
 * device effects; diverging shares mean the code path itself differs.
 */
object CrossDeviceSections {

    val SECTIONS: List<String> = listOf(
        "emb-embrace-impl-init", "emb-bootstrapper-init", "emb-modules-init", "emb-persisted-config-load",
        "emb-config-service-init", "emb-span-service-init", "emb-otel-tracer-init", "emb-essential-service-init",
        "emb-delivery-init", "emb-payload-source-init", "emb-post-init", "emb-post-services-setup",
        "emb-load-instrumentation", "emb-install-native-crash-signal-handlers", "emb-load-embrace-native-lib",
        "emb-record-startup",
    )

    /** `data` is label → pooled iterations, in first-seen label order. */
    fun report(data: Map<String, List<Record>>): String {
        val out = StringBuilder()
        val labels = data.keys.toList()
        out.line("iterations pooled: " + labels.joinToString("  ") { "$it=${data.getValue(it).size}" })
        out.line()
        val wmed = labels.associateWith { lb -> Quantile.median(data.getValue(lb).map { it.window() }.sorted()) }
        out.line("".padEnd(W44) + labels.joinToString("") { "${"$it med".padStart(W10)}${"$it max".padStart(W10)}" })
        out.line(
            "SDK-init window".padEnd(W44) + labels.joinToString("") { lb ->
                val wins = data.getValue(lb).map { it.window() }
                "${f(Quantile.median(wins.sorted()), 1, W10)}${f(wins.max(), 1, W10)}"
            },
        )
        out.line()
        out.line("sections (ms; %win = section median / device window median):")
        out.line(
            "section".padEnd(W44) + labels.joinToString("") {
                "${"$it med".padStart(W10)}${"$it max".padStart(W10)}${"%win".padStart(W6)}"
            },
        )
        SECTIONS.forEach { sec ->
            var present = false
            val row = StringBuilder(sec.padEnd(W44))
            labels.forEach { lb ->
                val vals = data.getValue(lb).mapNotNull { it.dur[sec] }
                if (vals.isNotEmpty()) {
                    present = true
                    val m = Quantile.median(vals.sorted())
                    row.append(f(m, 2, W10)).append(f(vals.max(), 2, W10)).append(f(m / wmed.getValue(lb) * PERCENT, 1, W6))
                } else {
                    row.append("--".padStart(W10)).append("--".padStart(W10)).append("--".padStart(W6))
                }
            }
            if (present) out.line(row.toString())
        }
        out.line()
        out.line("share of window, biggest three per device (med basis):")
        labels.forEach { lb ->
            val shares = SECTIONS.subList(SHARE_FROM, SHARE_TO).mapNotNull { sec ->
                val vals = data.getValue(lb).mapNotNull { it.dur[sec] }
                if (vals.isEmpty()) null else sec to Quantile.median(vals.sorted()) / wmed.getValue(lb) * PERCENT
            }.sortedByDescending { it.second }
            val top = shares.take(TOP_SHARES).joinToString(", ") { (s, v) -> "${s.removePrefix("emb-")} ${PyFormat.fixed(v, 0)}%" }
            out.line("  $lb: $top")
        }
        return out.toString()
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun f(x: Double, decimals: Int, width: Int) = PyFormat.fixed(x, decimals).padStart(width)

    private fun Record.window(): Double = requireNotNull(windowMs) { "$trace has no window_ms" }

    private const val SHARE_FROM = 3
    private const val SHARE_TO = 13
    private const val TOP_SHARES = 3
    private const val PERCENT = 100.0
    private const val W6 = 6
    private const val W10 = 10
    private const val W44 = 44
}
