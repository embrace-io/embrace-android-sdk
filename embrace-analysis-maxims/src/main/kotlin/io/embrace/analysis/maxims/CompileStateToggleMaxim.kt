package io.embrace.analysis.maxims

import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.stats.Quantile
import kotlin.math.abs

/** compile-state-toggle: pass medians alternate between two install-time compile states on some OEM builds. */
internal object CompileStateToggleMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "compile-state-toggle",
        scope = Maxims.DEVICE_SPECIFIC,
        statement = "Pass medians alternate between two levels at least 10% apart, with pure-CPU sections within 1.25x " +
            "and block-and-resume sections 1.5x or more between the two states.",
        why = "Install-time compile state alternating per reinstall on some OEM builds (hypothesis H2). Which devices " +
            "show it is the finding; compare only matching-state passes where they do.",
        check = ::checkToggle,
    )

    private fun checkToggle(c: Maxims.Campaign): Maxims.Verdict {
        val meds = c.passes.map { p -> Quantile.median(p.map { it.window }.sorted()) }
        if (meds.size < MIN_PASSES_FOR_TOGGLE) {
            return Maxims.Verdict(Maxims.THIN, "${meds.size} passes; the toggle needs at least 3")
        }
        val fast = c.passes[meds.indices.minByOrNull { meds[it] } ?: 0]
        val slow = c.passes[meds.indices.maxByOrNull { meds[it] } ?: 0]
        val pure = sectionRatio(fast, slow, Maxims.PURE_CPU)
        val block = sectionRatio(fast, slow, Maxims.BLOCK_RESUME)
        val seq = meds.joinToString(" -> ") { PyFormat.fixed(it, 1) }
        if (pure == null || block == null) {
            return Maxims.Verdict(Maxims.NA, "pass medians $seq; section medians missing")
        }
        val obs = "pass medians $seq; pure-CPU ${PyFormat.fixed(pure, 2)}x, block-resume ${PyFormat.fixed(block, 2)}x"
        val fingerprint = pure <= PURE_CPU_MAX_RATIO && block >= BLOCK_RESUME_MIN_RATIO
        return Maxims.Verdict(if (isAlternating(meds) && fingerprint) Maxims.CONFIRMED else Maxims.CONTRADICTED, obs)
    }

    /** Every step between consecutive pass medians is at least [Maxims.TOGGLE_STEP] and the steps alternate in sign. */
    private fun isAlternating(meds: List<Double>): Boolean {
        val steps = (0 until meds.size - 1).map { meds[it + 1] / meds[it] - 1.0 }
        return steps.all { abs(it) >= Maxims.TOGGLE_STEP } &&
            (0 until steps.size - 1).all { (steps[it] > 0) != (steps[it + 1] > 0) }
    }

    /** Median over [names] of the slow-pass / fast-pass section-median ratio; null when no section carries one. */
    private fun sectionRatio(
        fastPass: List<Maxims.Iteration>,
        slowPass: List<Maxims.Iteration>,
        names: List<String>,
    ): Double? {
        val rs = names.mapNotNull { name ->
            val f = medSection(fastPass, name)
            val s = medSection(slowPass, name)
            if (f != null && s != null && f > 0) {
                s / f
            } else {
                null
            }
        }
        return if (rs.isEmpty()) null else Quantile.median(rs.sorted())
    }

    private fun medSection(p: List<Maxims.Iteration>, name: String): Double? {
        val vals = p.mapNotNull { it.dur[name] }
        return if (vals.isEmpty()) null else Quantile.median(vals.sorted())
    }

    private const val MIN_PASSES_FOR_TOGGLE = 3
    private const val PURE_CPU_MAX_RATIO = 1.25
    private const val BLOCK_RESUME_MIN_RATIO = 1.5
}
