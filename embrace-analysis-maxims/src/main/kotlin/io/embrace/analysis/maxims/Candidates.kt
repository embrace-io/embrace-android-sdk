package io.embrace.analysis.maxims

import io.embrace.analysis.reports.OutlierFactors

/**
 * Candidates: strong associations no maxim covers.
 *
 * name -> (key function, lowest?) for every per-iteration factor present on all iterations. Every
 * duration or count is divided by the iteration's window first: a slow iteration is a long window, and
 * anything that accumulates over the window would correlate with slowness by construction.
 */
internal object Candidates {

    /** `(name, lift)` for every candidate factor whose quartile lift reaches [Maxims.CANDIDATE_LIFT], sorted by name. */
    internal fun of(c: Maxims.Campaign): List<Pair<String, Double>> {
        val factors = candidateFactors(c)
        val out = ArrayList<Pair<String, Double>>()
        factors.keys.sorted().forEach { name ->
            val (key, lowest) = factors.getValue(name)
            val group = Lifts.quartile(c.iterations, key, lowest) ?: return@forEach
            val (lift, _) = Lifts.liftOf(group, c.iterations)
            if (lift != null && lift >= Maxims.CANDIDATE_LIFT) {
                out.add(name to lift)
            }
        }
        return out
    }

    private fun candidateFactors(c: Maxims.Campaign): Map<String, Pair<(Maxims.Iteration) -> Double?, Boolean>> {
        if (!c.hasFactors) {
            return emptyMap()
        }
        val its = c.iterations
        val factors = LinkedHashMap<String, Pair<(Maxims.Iteration) -> Double?, Boolean>>()
        factors["D+io share"] = stateShare { k -> k.startsWith("D") && "+io" in k } to false
        factors["S share"] = stateShare { k -> k.startsWith("S") } to false
        factors["lock_contention share"] = scalarShare { it.lockContentionMs } to false
        factors["art_classload share"] = scalarShare { it.artClassloadMs } to false
        factors["art_verify share"] = scalarShare { it.artVerifyMs } to false
        factors["binder_txn per ms"] = scalarShare { it.binderTxnCnt } to false
        factors["eff_mhz"] = scalar { it.effMhz } to true
        factors["mem_available"] = scalar { it.memAvailable } to true
        competitorMeans(its).forEach { (name, mean) ->
            if (mean >= Maxims.COMPETITOR_MIN_SHARE) {
                factors["othercpu:$name share"] = competitorShare(name) to false
            }
        }
        return factors.filter { (_, kv) -> its.all { kv.first(it) != null } }
    }

    /** Each competing process's mean share of the window across the campaign, the idle task excluded. */
    private fun competitorMeans(its: List<Maxims.Iteration>): Map<String, Double> {
        val means = LinkedHashMap<String, Double>()
        its.forEach { iter ->
            requireNotNull(iter.factors).othercpu.forEach { (name, v) ->
                if (name != Maxims.IDLE_TASK) {
                    means[name] = (means[name] ?: 0.0) + v / iter.window / its.size
                }
            }
        }
        return means
    }

    private fun scalarShare(get: (OutlierFactors.Record) -> Double?): (Maxims.Iteration) -> Double? =
        { iter -> get(requireNotNull(iter.factors))?.let { v -> v / iter.window } }

    private fun scalar(get: (OutlierFactors.Record) -> Double?): (Maxims.Iteration) -> Double? =
        { iter -> get(requireNotNull(iter.factors)) }

    /** Summed ms of the thread states whose name (before any `:blocked_function`) satisfies [pred], over the window. */
    private fun stateShare(pred: (String) -> Boolean): (Maxims.Iteration) -> Double? = { iter ->
        val states = requireNotNull(iter.factors).states
        states.entries.filter { e -> pred(e.key.substringBefore(':')) }.sumOf { e -> e.value } / iter.window
    }

    private fun competitorShare(name: String): (Maxims.Iteration) -> Double? =
        { iter -> (requireNotNull(iter.factors).othercpu[name] ?: 0.0) / iter.window }
}
