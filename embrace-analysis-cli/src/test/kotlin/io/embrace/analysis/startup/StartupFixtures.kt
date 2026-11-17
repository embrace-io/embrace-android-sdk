package io.embrace.analysis.startup

import io.embrace.analysis.benchmark.IterationTrace
import java.nio.file.Paths

/** One trace's metrics: by default an ok trace with a 10 ms window, 300 ms TTID and no sections. */
internal fun startupMetrics(
    sections: Map<String, Double> = emptyMap(),
    windowMs: Double? = 10.0,
    source: StartupWindow.Source = StartupWindow.Source.SDK_START,
    waitMs: Double = 0.5,
    ttidMs: Double? = 300.0,
    health: TraceHealth = traceHealth(),
) = StartupMetrics(
    sections = sections,
    window = windowMs?.let {
        StartupWindow(
            durationMs = it,
            source = source,
            initThread = StartupWindow.ThreadScheduling(runningMs = 9.0, waitMs = waitMs, cpus = 2),
        )
    },
    ttidMs = ttidMs,
    health = health,
)

/** A trace's health: by default ok, nothing lost, with scheduling covering the window. */
internal fun traceHealth(
    verdict: TraceHealth.Verdict = TraceHealth.Verdict.OK,
    losses: List<TraceHealth.StatCounter> = emptyList(),
    importErrors: List<TraceHealth.StatCounter> = emptyList(),
    lossCountersReported: Boolean = true,
    bufferWraps: Long = 0,
    statsAfterData: Boolean = true,
    scheduling: TraceHealth.SchedulingCoverage = TraceHealth.SchedulingCoverage.FULL,
) = TraceHealth(
    verdict = verdict,
    losses = losses,
    importErrors = importErrors,
    lossCountersReported = lossCountersReported,
    bufferWraps = bufferWraps,
    statsAfterData = statsAfterData,
    scheduling = scheduling,
)

/** Iteration [index]'s trace, `iter00<index>_x`, with [metrics]. */
internal fun analyzedIteration(index: Int, metrics: StartupMetrics) = AnalyzedIteration(
    trace = IterationTrace(
        path = Paths.get("StartupBenchmarks_coldStartup_iter00${index}_x.perfetto-trace"),
        index = index,
        shortName = "iter00${index}_x",
    ),
    metrics = metrics,
)
