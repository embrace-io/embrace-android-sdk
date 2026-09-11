package io.embrace.analysis.maxims

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.stats.Quantile

/**
 * config-fast-path: every pass's first launch takes the fresh-install config fast path.
 *
 * Tier-relative on purpose: the fresh path is a fraction of the cached path on every tier, but the
 * absolute numbers span an order of magnitude between flagship and entry.
 */
internal object ConfigFastPathMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "config-fast-path",
        scope = Maxims.UNIVERSAL,
        statement = "Every pass's first launch takes the fresh-install config fast path: its persisted-config-load is " +
            "below the median of the rest of the pass.",
        why = "iter000 has no cached config to decode. A violation means app data survived a failed uninstall and the " +
            "pass's first-launch sample is poisoned (hypothesis H3); the check is tier-relative because the two bands " +
            "scale with the device. An arm that clears app data before every launch is n/a: every launch takes the " +
            "fast path there, so the comparison has no second population.",
        check = ::checkConfigFastPath,
    )

    private fun checkConfigFastPath(c: Maxims.Campaign): Maxims.Verdict {
        // The check reads iter000 against the rest because only iter000 starts without a cached config.
        // An arm that wipes app data before every launch gives every launch the fast path, so the two
        // sides are the same population and the comparison is noise either way.
        dataResetMethod(c)?.let {
            return Maxims.Verdict(Maxims.NA, "$it clears app data before every launch: all take the fast path")
        }
        val bad = ArrayList<String>()
        var seen = 0
        c.passes.forEach { p ->
            val first = p[0].dur[Maxims.CFGLOAD]
            val rest = p.drop(1).mapNotNull { it.dur[Maxims.CFGLOAD] }
            if (first == null || rest.isEmpty()) {
                return@forEach
            }
            seen += 1
            val rmed = Quantile.median(rest.sorted())
            if (first >= rmed) {
                bad.add("pass ${p[0].passNo}: iter000 ${PyFormat.fixed(first, 1)} ms vs rest p50 ${PyFormat.fixed(rmed, 1)} ms")
            }
        }
        if (seen == 0) {
            return Maxims.Verdict(Maxims.NA, "${Maxims.CFGLOAD} not in the datasets")
        }
        if (bad.isNotEmpty()) {
            return Maxims.Verdict(Maxims.CONTRADICTED, "${bad.size} of $seen passes; " + bad[0])
        }
        return Maxims.Verdict(Maxims.CONFIRMED, "every pass's iter000 below the rest's median over $seen passes")
    }

    /**
     * The provenance's benchmark method when it is one that clears the app's data before every launch
     * (rather than once per pass), else null. Named rather than inferred: the harness owns these method
     * names, and the alternative - guessing from the data - cannot tell "every launch took the fast path"
     * from "the decode is simply cheap on this device".
     */
    private fun dataResetMethod(c: Maxims.Campaign): String? {
        val method = PyJson.strOrNull(c.meta, "method") ?: return null
        return method.takeIf { m -> DATA_RESET_METHODS.any { m.contains(it) } }
    }

    /**
     * Fragments of the harness method names that clear the app's data before EVERY launch, not once per
     * pass. Add one here when a new arm does the same, or `config-fast-path` will contradict on it for a
     * reason that is the arm's design rather than a finding.
     */
    private val DATA_RESET_METHODS = listOf("NewUserSession")
}
