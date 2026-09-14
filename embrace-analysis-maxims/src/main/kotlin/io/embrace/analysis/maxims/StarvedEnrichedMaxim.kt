package io.embrace.analysis.maxims

import io.embrace.analysis.common.text.PyFormat

/** starved-enriched: starved inits (high runnable-wait share) are enriched among slow inits. */
internal object StarvedEnrichedMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "starved-enriched",
        scope = Maxims.UNIVERSAL,
        statement = "Starved inits (runnable-wait share of 20% or more) are at least 3x enriched among slow inits.",
        why = "Rare on a quiet bench, and decisive when it happens; the production tool uses the same 20% cutoff.",
        check = ::checkStarved,
    )

    private fun checkStarved(c: Maxims.Campaign): Maxims.Verdict {
        if (!c.hasFactors) {
            return Maxims.Verdict(Maxims.NA, "no passN-factors.json")
        }
        val starved = c.iterations.filter { it.rqShare != null && it.rqShare >= Maxims.STARVED_SHARE }
        val slow = c.iterations.filter { it.slow }
        if (starved.size < Maxims.MIN_SLOW || slow.size < Maxims.MIN_SLOW) {
            return Maxims.Verdict(Maxims.THIN, "${starved.size} starved and ${slow.size} slow iterations of ${c.iterations.size}")
        }
        val shareAll = starved.size.toDouble() / c.iterations.size
        val shareSlow = slow.count { it in starved }.toDouble() / slow.size
        val enrichment = shareSlow / shareAll
        val obs = "${PyFormat.fixed(enrichment, 2)}x enrichment, ${starved.size} starved iterations"
        return Maxims.Verdict(if (enrichment >= ENRICHMENT_FLOOR) Maxims.CONFIRMED else Maxims.CONTRADICTED, obs)
    }

    private const val ENRICHMENT_FLOOR = 3.0
}
