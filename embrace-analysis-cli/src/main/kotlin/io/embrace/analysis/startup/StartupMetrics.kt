package io.embrace.analysis.startup

import io.embrace.analysis.benchmark.CaptureProfile

/**
 * What one trace says about SDK startup: the duration in ms of the first occurrence of every `emb-*` section, by name in
 * name order; the SDK-init [window], if the trace has one; the cold start's TTID in ms, if trace_processor found one;
 * the trace's [health], which says how far the rest can be trusted; and the [capture] profile it was recorded with,
 * which says what it could hold.
 */
data class StartupMetrics(
    val sections: Map<String, Double>,
    val window: StartupWindow?,
    val ttidMs: Double?,
    val health: TraceHealth,
    val capture: CaptureProfile,
)
