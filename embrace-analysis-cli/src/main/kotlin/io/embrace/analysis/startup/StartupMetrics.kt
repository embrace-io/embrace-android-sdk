package io.embrace.analysis.startup

/**
 * What one trace says about SDK startup: the duration in ms of the first occurrence of every `emb-*` section, by name in
 * name order; the SDK-init [window], if the trace has one; and the cold start's TTID in ms, if trace_processor found
 * one.
 */
data class StartupMetrics(
    val sections: Map<String, Double>,
    val window: StartupWindow?,
    val ttidMs: Double?,
)
