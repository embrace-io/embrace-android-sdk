package io.embrace.analysis.campaign

import io.embrace.analysis.perfetto.TraceHealth
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.records.store.StartupHealth
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * The post-run check: the traces a cell produced carry the window instrument. Two failures look
 * identical in the output and must be told apart - a wrong SDK/patch (the window instrument does not
 * exist) and a saturated capture (it existed but got evicted) - so this reads the canary AND the loss
 * counters, on a sample from both ends of the cell because saturation worsens as a pass accumulates load.
 *
 * Hard-coding the canary as `app-embrace-start` was once wrong (the harness never emitted that slice), so
 * the instrument name is the plan's, supplied by the caller.
 */
class InstrumentPresent(private val traceProcessor: (() -> TraceProcessor)?) {

    fun check(traceDir: Path, instrument: String): Check {
        val traces = Files.walk(traceDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".perfetto-trace") }.toList()
        }.sorted()
        if (traces.isEmpty()) {
            return Check(NAME, false, "no traces produced")
        }
        val tp = runCatching { traceProcessor?.invoke() }.getOrNull()
            ?: return Check(NAME, true, "${traces.size} traces (trace_processor unavailable; UNVERIFIED)")
        val sample = if (traces.size > SAMPLE_EDGE * 2) {
            traces.take(SAMPLE_EDGE) + traces.takeLast(SAMPLE_EDGE)
        } else {
            traces
        }
        val profile = StartupHealth.profileFor(instrument)
        val verdicts = sample.map { TraceHealth.check(tp, it, profile) }
        val report = TraceHealth.summarize(verdicts, profile)
        val saturated = "$report | capture saturated and evicted the window - fix the trace config " +
            "(buffer size / DISCARD / narrower events) before re-running"
        val noCanary = "$report | no data loss but no '$instrument' anywhere - wrong SDK, wrong build, " +
            "or a missing atrace category"
        return when {
            verdicts.any { it.verdict == TraceHealth.Verdict.UNUSABLE } -> Check(NAME, false, saturated)
            verdicts.all { it.verdict == TraceHealth.Verdict.MISSING_CANARY } -> Check(NAME, false, noCanary)
            else -> Check(NAME, true, report)
        }
    }

    companion object {
        const val NAME: String = "instrument"
        private const val SAMPLE_EDGE = 5
    }
}
