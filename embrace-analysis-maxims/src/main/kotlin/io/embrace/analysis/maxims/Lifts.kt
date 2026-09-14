package io.embrace.analysis.maxims

import io.embrace.analysis.common.text.PyFormat

/** Lift arithmetic shared by the quartile-based maxims: quartile selection, lift ratio, and verdict banding. */
internal object Lifts {

    /** The lowest (or highest) quartile of iterations by key, ties broken by run order; null if thin. */
    internal fun quartile(
        iterations: List<Maxims.Iteration>,
        key: (Maxims.Iteration) -> Double?,
        lowest: Boolean,
    ): List<Maxims.Iteration>? {
        val valued = iterations.filter { key(it) != null }
        val k = valued.size / 4
        if (k < Maxims.MIN_GROUP) {
            return null
        }
        // Sort order: by value first (floats compared with `<`/`==`, so -0.0 ties 0.0), then pass_no, then index.
        val ordered = valued.sortedWith { a, b ->
            val ka = requireNotNull(key(a))
            val kb = requireNotNull(key(b))
            when {
                ka < kb -> -1
                ka > kb -> 1
                a.passNo != b.passNo -> a.passNo.compareTo(b.passNo)
                else -> a.index.compareTo(b.index)
            }
        }
        return if (lowest) ordered.take(k) else ordered.takeLast(k)
    }

    /** `(lift, observed)` for a group, or `(null, reason)` when the campaign cannot carry one. */
    internal fun liftOf(group: List<Maxims.Iteration>, iterations: List<Maxims.Iteration>): Pair<Double?, String> {
        val baseSlow = iterations.count { it.slow }
        if (baseSlow < Maxims.MIN_SLOW) {
            return null to "only $baseSlow slow iterations of ${iterations.size}"
        }
        val gSlow = group.count { it.slow }
        val baseRate = baseSlow.toDouble() / iterations.size
        val lift = (gSlow.toDouble() / group.size) / baseRate
        return lift to "${PyFormat.fixed(lift, 2)}x on ${group.size} iterations " +
            "(slow $gSlow/${group.size} vs $baseSlow/${iterations.size})"
    }

    internal fun directionalStatus(lift: Double, floor: Double): String = when {
        lift >= floor -> Maxims.CONFIRMED
        lift < Maxims.NULL_LOW -> Maxims.CONTRADICTED
        else -> Maxims.UNDETECTED
    }

    internal fun liftCheck(
        key: (Maxims.Iteration) -> Double?,
        lowest: Boolean,
        floor: Double,
        directional: Boolean,
        needsFactors: Boolean = true,
    ): (Maxims.Campaign) -> Maxims.Verdict = { c ->
        if (needsFactors && !c.hasFactors) {
            Maxims.Verdict(Maxims.NA, "no passN-factors.json")
        } else {
            val group = quartile(c.iterations, key, lowest)
            if (group == null) {
                Maxims.Verdict(Maxims.THIN, "quartile under ${Maxims.MIN_GROUP} iterations")
            } else {
                val (lift, obs) = liftOf(group, c.iterations)
                when {
                    lift == null -> Maxims.Verdict(Maxims.THIN, obs)
                    directional -> Maxims.Verdict(directionalStatus(lift, floor), obs)
                    lift >= floor -> Maxims.Verdict(Maxims.CONFIRMED, obs)
                    else -> Maxims.Verdict(Maxims.CONTRADICTED, obs)
                }
            }
        }
    }
}
