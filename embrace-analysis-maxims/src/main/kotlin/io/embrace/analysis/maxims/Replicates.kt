package io.embrace.analysis.maxims

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.stats.Descriptive
import java.nio.file.Path
import kotlin.math.sqrt

/**
 * The layer above a campaign: treat each campaign with a given set of parameters as ONE draw from the
 * population of campaigns those parameters define, and ask how much the findings move between draws.
 *
 * A multi-leg campaign estimates the dispersion its legs expose - install parity, thermal state, time
 * of night - and the cluster bootstrap resamples legs. Everything the legs share is invisible to it: the
 * day, the device's condition that week, host state, the build. That variance shows up only between
 * replicates, and if it is large where the within-campaign interval was narrow, the interval was
 * overconfident. The same holds for verdicts: a maxim whose status flips between replicates of one cell
 * is unstable, whatever one campaign said.
 *
 * Within-campaign spread here is the standard error of the pass medians (their sample SD over the square
 * root of the pass count), the same quantity the leg-level inference rests on; between-campaign spread is
 * the sample SD of the replicate medians. The ratio is read against the small-N caveat printed with it:
 * five replicates pin a standard deviation only to within about half of itself.
 */
object Replicates {

    fun report(records: Path, serials: DeviceSerials.Serials = DeviceSerials.Serials.EMPTY): String {
        val entries = EvidenceIndex.build(records, serials)
        val groups = entries.groupBy { it.entry.replicateKey }.filterValues { it.size >= 2 }
        val cells = entries.map { it.entry.replicateKey }.toSet().size
        if (groups.isEmpty()) {
            return "replicates: no cell has more than one campaign yet (${entries.size} archives, $cells cells)\n"
        }
        val out = StringBuilder()
        out.append("replicates: ${groups.size} cell(s) measured more than once, of $cells\n")
        groups.toSortedMap().forEach { (key, reps) ->
            val ordered = reps.sortedBy { it.replicateIndex }
            out.append("\n$key  (${ordered.size} replicates)\n")
            out.append(
                "  ${"run".padEnd(
                    RUN_W,
                )} ${"started".padEnd(
                    STARTED_W,
                )} ${"n".padStart(N_W)} ${"median".padStart(NUM_W)} ${"p90".padStart(NUM_W)} ${"p95".padStart(NUM_W)}  evidence\n",
            )
            ordered.forEach { r ->
                val e = r.entry
                out.append(
                    "  ${e.runId.take(RUN_W).padEnd(RUN_W)} ${(e.started ?: "-").take(STARTED_W).padEnd(STARTED_W)} " +
                        "${e.launches.toString().padStart(N_W)} ${PyFormat.fixed(e.median, 2).padStart(NUM_W)} " +
                        "${PyFormat.fixed(e.p90, 2).padStart(NUM_W)} ${PyFormat.fixed(e.p95, 2).padStart(NUM_W)}  ${e.evidence.klass}\n",
                )
            }
            out.append(spreadLines(ordered.map { it.entry }))
            out.append(stabilityLines(records, ordered.map { it.entry }))
        }
        out.append(
            "\nread with care: between-campaign SDs from ${groups.values.minOf { it.size }}-${groups.values.maxOf { it.size }} " +
                "replicates are rough (five replicates pin an SD only to within about half of itself); the flag marks " +
                "cells where the within-campaign interval was visibly overconfident, not a measurement of by how much.\n",
        )
        return out.toString()
    }

    private fun spreadLines(reps: List<EvidenceIndex.Entry>): String {
        val medians = reps.map { it.median }
        val between = Descriptive.sampleStdev(medians)
        val within = reps.mapNotNull { e ->
            if (e.passMedians.size < 2) null else Descriptive.sampleStdev(e.passMedians) / sqrt(e.passMedians.size.toDouble())
        }
        val withinMean = if (within.isEmpty()) null else within.average()
        val sb = StringBuilder()
        val p95Low = PyFormat.fixed(reps.minOf { it.p95 }, 2)
        val p95High = PyFormat.fixed(reps.maxOf { it.p95 }, 2)
        sb.append(
            "  between campaigns: median SD ${PyFormat.fixed(between, 2)} ms, range ${PyFormat.fixed(medians.min(), 2)}-" +
                "${PyFormat.fixed(medians.max(), 2)} ms; p95 range $p95Low-$p95High ms\n",
        )
        if (withinMean == null) {
            sb.append("  within a campaign: single-pass campaigns carry no pass-level spread to compare against\n")
            return sb.toString()
        }
        val ratio = if (withinMean > 0) between / withinMean else Double.POSITIVE_INFINITY
        val flag = if (ratio > OVERCONFIDENT_RATIO) {
            "  <- between-campaign variance dominates: the within-campaign interval was overconfident"
        } else {
            ""
        }
        sb.append(
            "  within a campaign: pass-median SE ${PyFormat.fixed(withinMean, 2)} ms (mean over replicates); " +
                "between/within ${PyFormat.fixed(ratio, 2)}x$flag\n",
        )
        return sb.toString()
    }

    /** Re-score each replicate (no traces needed) and name the maxims whose status differs between them. */
    private fun stabilityLines(records: Path, reps: List<EvidenceIndex.Entry>): String {
        val statuses = LinkedHashMap<String, MutableList<String>>()
        reps.forEach { e ->
            val dir = Zips.unpackToTemp(records.resolve("campaigns").resolve("${e.runId}.zip"))
            val loaded = Maxims.loadCampaign(dir)
            Maxims.score(loaded).forEach { (m, v) -> statuses.getOrPut(m.id) { ArrayList() }.add(v.status) }
        }
        val unstable = statuses.filterValues { it.toSet().size > 1 }
        if (unstable.isEmpty()) {
            return "  verdicts: every maxim reads the same on all replicates\n"
        }
        return unstable.entries.joinToString("") { (id, list) ->
            "  verdict unstable: ${id.padEnd(ID_W)} ${list.joinToString(" / ")}\n"
        }
    }

    private const val OVERCONFIDENT_RATIO = 2.0
    private const val RUN_W = 44
    private const val STARTED_W = 19
    private const val N_W = 4
    private const val NUM_W = 8
    private const val ID_W = 22
}
