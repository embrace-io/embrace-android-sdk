package io.embrace.analysis.maxims

import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.stats.Quantile

/** first-launch: the first launch after an install is slower than the rest of its pass. */
internal object FirstLaunchMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "first-launch",
        scope = Maxims.DIRECTIONAL,
        statement = "The first launch after an install is slower than the rest of its pass, by 1.15x or more where " +
            "measurable.",
        why = "Install aftermath: dexopt and system bursts. The bench also found the fresh-install config fast path " +
            "can cancel it on fast tiers, which is why this is directional and why a contradiction here is expected " +
            "on flagships.",
        check = ::checkFirstLaunch,
    )

    private fun checkFirstLaunch(c: Maxims.Campaign): Maxims.Verdict {
        val ratios = ArrayList<Double>()
        c.passes.forEach { p ->
            if (p.size < MIN_PASS_FOR_FIRST_LAUNCH) {
                return@forEach
            }
            val rest = Quantile.median(p.drop(1).map { it.window }.sorted())
            if (rest > 0) {
                ratios.add(p[0].window / rest)
            }
        }
        if (ratios.size < 2) {
            return Maxims.Verdict(Maxims.THIN, "${ratios.size} passes with a first launch and a rest")
        }
        val ratio = Quantile.median(ratios.sorted())
        return Maxims.Verdict(
            Lifts.directionalStatus(ratio, Maxims.NULL_HIGH),
            "iter000/rest ${PyFormat.fixed(ratio, 2)}x over ${ratios.size} passes",
        )
    }

    private const val MIN_PASS_FOR_FIRST_LAUNCH = 3
}
