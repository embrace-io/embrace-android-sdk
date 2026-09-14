package io.embrace.analysis.perfetto

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * Detect trace data loss BEFORE trusting any number derived from a trace, with verdict logic and
 * wording kept intact from the previous toolchain.
 *
 * Perfetto does not fail loudly when it drops data: a saturated ring buffer silently evicts the
 * oldest packets - exactly the app slices at the start of a launch - and the trace still parses and
 * answers queries. But a missing window is NOT by itself evidence of loss; the same symptom comes,
 * more often, from a query bug. So this reads Perfetto's own counters and lets them decide, and
 * buckets each counter by WHAT A NON-ZERO VALUE CAN INVALIDATE:
 *
 * - [Bucket.BUFFER]: written data was lost (central buffer wrapped, writer dropped packets, ftrace
 *   overran). The only kind that can remove a window or a chunk of sched. Real, and rare.
 * - [Bucket.PARSE]: individual events failed to parse or arrived out of order. Surviving slices keep
 *   correct timestamps and durations, but counts and presence claims are no longer safe. Clusters by
 *   device in practice.
 * - [Bucket.META]: streams carrying no slices and no scheduling (memory stats, log lines, power
 *   rails). Noise for startup work. An earlier version that counted these condemned 88% of a good
 *   596-trace corpus.
 *
 * An unrecognised counter is bucketed as PARSE, not ignored, and reported by name.
 *
 * Two independent checks, because either alone can be fooled: the loss counters, and a canary slice
 * that must exist in every good trace. Canary absent with clean buffer counters = a query bug, wrong
 * build or missing instrument, NOT saturation; canary absent WITH buffer loss = saturation.
 *
 * What "healthy" means for a particular kind of trace - which slice is the canary, which section's
 * class-load burst fingerprints a regression and at what count - is a [Profile] the caller supplies.
 * Nothing here knows what is being measured; the startup profile lives with the records that use it.
 */
object TraceHealth {

    /**
     * What a healthy trace looks like for one kind of measurement: the [canary] slice every good trace
     * must contain, the [burstSection] whose ART class-load count on the init thread fingerprints a
     * regression when it reaches [burstThreshold], and the [burstAdvice] the summary prints when it does.
     */
    data class Profile(
        val canary: String,
        val burstSection: String,
        val burstThreshold: Long,
        val burstAdvice: String,
    )

    enum class Bucket(val key: String) { BUFFER("buffer"), PARSE("parse"), META("meta") }

    enum class Verdict(val key: String) {
        OK("ok"),
        LOSSY("lossy"),
        UNUSABLE("unusable"),
        SLICES_INCOMPLETE("slices-incomplete"),
        MISSING_CANARY("missing-canary"),
    }

    data class Report(
        val trace: String,
        val verdict: Verdict,
        val reason: String,
        val canarySlices: Long,
        /** Every non-zero `data_loss`/`error` counter, by name. */
        val losses: Map<String, Long>,
        val buckets: Map<Bucket, Map<String, Long>>,
        val slices: Long,
        val schedRows: Long,
        /** ART class-load slices on the init thread inside the profile's burst section; null when not measured. */
        val classLoadsInBurstSection: Long? = null,
        /** [classLoadsInBurstSection] reached the profile's threshold: the runtime-serializer regression signature. */
        val classLoadBurst: Boolean = false,
    ) {
        /** Durations read from the window are trustworthy. */
        val windowsOk: Boolean get() = verdict == Verdict.OK || verdict == Verdict.SLICES_INCOMPLETE || verdict == Verdict.LOSSY

        /** Count- or presence-shaped metrics over the whole trace are trustworthy. */
        val countsOk: Boolean get() = verdict == Verdict.OK
    }

    /** One query, because each `trace_processor` invocation re-parses the whole trace. */
    fun sql(canary: String): String = """
SELECT 'loss.' || name AS k, CAST(SUM(value) AS INT) AS v
  FROM stats
 WHERE severity IN ('data_loss', 'error') AND value > 0
 GROUP BY name
UNION ALL
SELECT 'canary', (SELECT COUNT(*) FROM slice WHERE name = '$canary')
UNION ALL
SELECT 'slices', (SELECT COUNT(*) FROM slice)
UNION ALL
SELECT 'sched_rows', (SELECT COUNT(*) FROM sched);
"""

    /**
     * A second, separate query (so the frozen health goldens stay byte-identical): ART class-load slices
     * (`Lpkg/Cls;`) on the same thread inside the first occurrence of [section].
     */
    fun classLoadSql(section: String): String = """
SELECT 'classloads' AS k, COUNT(*) AS v
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN (SELECT s2.ts AS ts, s2.ts + s2.dur AS end_ts, tt2.utid AS utid
          FROM slice s2 JOIN thread_track tt2 ON s2.track_id = tt2.id
         WHERE s2.name = '$section' ORDER BY s2.ts LIMIT 1) w
 WHERE tt.utid = w.utid AND s.ts >= w.ts AND s.ts < w.end_ts AND s.name GLOB 'L*;';
"""

    /** Bucket a Perfetto stats counter by what a non-zero value can invalidate. */
    fun classify(counterName: String): Bucket = when {
        BUFFER_MARKERS.any { it in counterName } -> Bucket.BUFFER
        META_MARKERS.any { it in counterName } -> Bucket.META
        else -> Bucket.PARSE
    }

    /**
     * The health query's stdout parse: naive comma split, quotes stripped,
     * two-field lines only, header (`k`) skipped, values truncated through `int(float(v))`.
     */
    fun parseRows(stdout: String): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        stdout.lineSequence().forEach { line ->
            val parts = line.split(",").map { it.trim().trim('"') }
            if (parts.size == 2 && parts[0] != "k" && parts[0].isNotEmpty()) {
                parts[1].toDoubleOrNull()?.let { out[parts[0]] = it.toLong() }
            }
        }
        return out
    }

    /**
     * Both queries run against ONE load of the trace. Asking [TraceProcessor] twice re-parses it twice,
     * and a full parse is the dominant cost per trace; `Ingest` already shares a load the same way.
     */
    fun check(tp: TraceProcessor, trace: Path, profile: Profile): Report =
        tp.withWarmTrace(trace) { target ->
            val rows = parseRows(target.queryRaw(sql(profile.canary)).stdout)
            val classLoads = parseRows(target.queryRaw(classLoadSql(profile.burstSection)).stdout)[CLASS_LOADS_KEY]
            evaluate(trace.toString(), rows, classLoads, profile.burstThreshold)
        }

    /**
     * Verdict from already-parsed rows; split out so goldens can drive it without a binary. The class-load
     * burst is flagged only when [burstThreshold] is given AND [classLoadsInBurstSection] was measured.
     */
    fun evaluate(
        trace: String,
        rows: Map<String, Long>,
        classLoadsInBurstSection: Long? = null,
        burstThreshold: Long? = null,
    ): Report {
        val losses = rows.filterKeys { it.startsWith(LOSS_PREFIX) }
            .filterValues { it != 0L }
            .mapKeys { it.key.removePrefix(LOSS_PREFIX) }
        val buckets = Bucket.values().associateWith { LinkedHashMap<String, Long>() }
        losses.forEach { (name, value) -> buckets.getValue(classify(name))[name] = value }
        val canaryCount = rows[CANARY_KEY] ?: 0L
        val bufferLoss = buckets.getValue(Bucket.BUFFER).isNotEmpty()
        val parseLoss = buckets.getValue(Bucket.PARSE).isNotEmpty()

        val (verdict, reason) = when {
            bufferLoss && canaryCount == 0L ->
                Verdict.UNUSABLE to
                    "buffer-level data loss AND no canary slice - the capture saturated and evicted " +
                    "the window; fix the config (see prevention below), do not analyse this trace"
            bufferLoss ->
                Verdict.LOSSY to
                    "buffer-level data loss but the canary survived - window durations may be " +
                    "usable, but anything counting events across the whole trace is not"
            canaryCount == 0L ->
                Verdict.MISSING_CANARY to
                    "no data loss at all, but the canary slice is absent - NOT eviction: suspect a " +
                    "query bug (e.g. a process-name predicate), the wrong build, a missing atrace " +
                    "category, or an instrument that does not exist in this version"
            parseLoss ->
                Verdict.SLICES_INCOMPLETE to
                    "event-parse errors only: surviving slices keep correct timestamps and " +
                    "durations, so window and duration numbers stand, but counts and 'signal was " +
                    "absent' claims over this trace do not - an unparsed atrace line is an absent " +
                    "slice. Usually clusters by device; report it as a per-device caveat"
            else -> Verdict.OK to ""
        }
        return Report(
            trace = trace,
            verdict = verdict,
            reason = reason,
            canarySlices = canaryCount,
            losses = losses,
            buckets = buckets,
            slices = rows["slices"] ?: 0L,
            schedRows = rows["sched_rows"] ?: 0L,
            classLoadsInBurstSection = classLoadsInBurstSection,
            classLoadBurst = burstThreshold != null && classLoadsInBurstSection != null && classLoadsInBurstSection >= burstThreshold,
        )
    }

    /** The run-level summary lines, including prevention/caveat guidance; the REGRESSION line is worded by [profile]. */
    fun summarize(reports: List<Report>, profile: Profile): String {
        val counts = reports.groupingBy { it.verdict }.eachCount()
        val total = reports.size.coerceAtLeast(1)
        val clean = counts[Verdict.OK] ?: 0
        val lines = ArrayList<String>()
        lines.add(
            "trace health: $clean/$total clean" +
                counts.filterKeys { it != Verdict.OK }.entries.sortedBy { it.key.key }
                    .joinToString("") { ", ${it.value} ${it.key.key}" },
        )
        val bufferHit = (counts[Verdict.LOSSY] ?: 0) + (counts[Verdict.UNUSABLE] ?: 0)
        if (bufferHit > 0) {
            lines.add(
                "PREVENTION ($bufferHit/$total lost written data): raise buffer_size_kb, shorten " +
                    "duration_ms, switch fill_policy to DISCARD (keeps the EARLIEST data, which is where " +
                    "the init window is), narrow ftrace events and atrace categories, and give app slices " +
                    "their own buffer via target_buffer so a sched flood cannot evict them.",
            )
        }
        counts[Verdict.SLICES_INCOMPLETE]?.let { n ->
            lines.add(
                "CAVEAT ($n/$total had event-parse errors): duration and " +
                    "window figures stand; do not make count-based or 'signal absent' claims from these " +
                    "traces without checking whether the affected traces cluster on one device. Narrowing " +
                    "atrace categories reduces userspace-side parse failures.",
            )
        }
        counts[Verdict.MISSING_CANARY]?.let { n ->
            lines.add(
                "INVESTIGATE ($n/$total missing the canary with CLEAN loss " +
                    "counters): this is a query, build, or instrument problem, not saturation. Do not " +
                    "'fix' it by enlarging buffers.",
            )
        }
        val bursts = reports.count { it.classLoadBurst }
        if (bursts > 0) {
            lines.add(
                "REGRESSION ($bursts/$total loaded >= ${profile.burstThreshold} classes inside " +
                    "${profile.burstSection} on the init thread): ${profile.burstAdvice}",
            )
        }
        return lines.joinToString("\n")
    }

    /**
     * One report as the command prints it: `  [<verdict>          ] <file>  canary=<n> k=v, k=v` - the buffer
     * and parse counters, and the metadata-only ones only on request, since they are noise. The format is
     * pinned by the frozen CLI output.
     */
    fun perTraceLine(report: Report, trace: Path, showMeta: Boolean): String {
        val shown = LinkedHashMap<String, Long>()
        shown.putAll(report.buckets.getValue(Bucket.BUFFER))
        shown.putAll(report.buckets.getValue(Bucket.PARSE))
        if (showMeta) shown.putAll(report.buckets.getValue(Bucket.META))
        val detail = shown.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "none" }
        val burst = if (report.classLoadBurst) {
            "  burst-section-classloads=${report.classLoadsInBurstSection}"
        } else {
            ""
        }
        return "  [${report.verdict.key.padEnd(VERDICT_W)}] ${trace.fileName}  canary=${report.canarySlices} $detail$burst"
    }

    /**
     * Every trace under [dir], recursively, in the order the goldens pin: all `*.perfetto-trace` sorted,
     * then all `*.pftrace` sorted. The per-trace lines come out in this order, so it is what a reader
     * matches against the run directory.
     */
    fun listTraces(dir: Path): List<Path> {
        fun withSuffix(suffix: String) = Files.walk(dir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(suffix) }.toList()
        }.sorted()
        return withSuffix(".perfetto-trace") + withSuffix(".pftrace")
    }

    private const val VERDICT_W = 17
    private const val LOSS_PREFIX = "loss."
    private const val CANARY_KEY = "canary"
    private const val CLASS_LOADS_KEY = "classloads"

    private val BUFFER_MARKERS = listOf(
        "chunks_overwritten",
        "packet_loss",
        "abi_violations",
        "ftrace_cpu_overrun",
        "trace_writer_packet_loss",
        "chunks_discarded",
        "packets_lost",
    )
    private val META_MARKERS = listOf(
        "mm_unknown_type", "meminfo_unknown", "vmstat_unknown", "android_log_",
        "metatrace_", "energy_", "power_rail", "battery_", "gpu_counter",
        "clock_sync_cache_miss", "frame_timeline_event_parser",
    )
}
