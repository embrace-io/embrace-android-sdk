package io.embrace.analysis.startup

import io.embrace.analysis.benchmark.IterationTrace

/** An iteration's trace with the startup metrics read from it. */
data class AnalyzedIteration(val trace: IterationTrace, val metrics: StartupMetrics)
