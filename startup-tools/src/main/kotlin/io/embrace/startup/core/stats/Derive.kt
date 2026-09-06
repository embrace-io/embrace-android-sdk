package io.embrace.startup.core.stats

import io.embrace.startup.core.json.Derived

/**
 * The store's `derived` aggregates, computed the same way the existing records were - a PRESERVE-class port.
 *
 * Uses [Quantile.legacyIndex] deliberately: every `derived.p90/p95` on disk and every published
 * version table used this definition, so it must stay reproducible. New analyses use
 * [Quantile.type7]; the two are never mixed in one figure without saying so.
 *
 * One subtlety the goldens exposed: ingest computed `derived` on the UNROUNDED windows and then
 * rounded the windows to 3 dp before writing them, so `derive(record.windowsMs)` can differ from
 * `record.derived` in the fourth decimal. Parity is therefore asserted against the golden - the
 * recomputed output on the SAME (rounded) input - not against the stored field.
 */
object Derive {

    /** `derive(windows)`; returns null for an empty input where the golden record has `{}`. */
    fun of(windows: List<Double>): Derived? {
        if (windows.isEmpty()) return null
        val values = windows.sorted()
        fun pct(p: Double) = Quantile.legacyIndex(values, p)
        return Derived(
            n = values.size,
            median = Quantile.median(values),
            p90 = pct(0.90),
            p95 = pct(0.95),
            max = values.last(),
            iqr = if (values.size >= 4) pct(0.75) - pct(0.25) else 0.0,
        )
    }
}
