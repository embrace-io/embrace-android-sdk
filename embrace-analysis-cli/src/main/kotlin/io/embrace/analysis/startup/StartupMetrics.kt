package io.embrace.analysis.startup

/**
 * What one trace says about SDK startup: the duration in ms of the first occurrence of every `emb-*` section, by name in
 * name order; the SDK-init [window], if the trace has one; the cold start's TTID in ms, if trace_processor found one;
 * and the trace's [health], which says how far the rest can be trusted.
 */
data class StartupMetrics(
    val sections: Map<String, Double>,
    val window: StartupWindow?,
    val ttidMs: Double?,
    val health: TraceHealth,
)
