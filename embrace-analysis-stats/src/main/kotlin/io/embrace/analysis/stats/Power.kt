package io.embrace.analysis.stats

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Sizing and sanity arithmetic: how many launches a question needs, how small
 * an effect a design can see, and the two guards against over-reading a number (`dilution`,
 * `practical`).
 *
 * Operation order is fixed so `ceil` lands on the same integer as the goldens. This writes `x * x`
 * rather than `x ** 2`; CPython's float power for an exponent of 2 is the same correctly rounded
 * product.
 */
object Power {

    /**
     * `required_n`: iterations per arm to detect `effectPct` given variability `cvPct`, at 80% power
     * (or 70% for any `power` below 0.8 - exactly these two z values are supported) and two-sided
     * α = 0.05, inflated by the design effect. Halving the effect QUADRUPLES the sample.
     */
    fun requiredN(cvPct: Double, effectPct: Double, power: Double = DEFAULT_POWER, deff: Double = 1.0): Int? {
        if (effectPct == 0.0) return null
        val z = Z_ALPHA + zBeta(power)
        val n = 2 * (z * z) * (cvPct * cvPct) / (effectPct * effectPct)
        return ceil(n * maxOf(1.0, deff)).toInt()
    }

    /**
     * `min_detectable_effect`: the smallest difference this design could detect. Report it beside every
     * null result - "no significant change" means nothing until you say what you were able to see.
     */
    fun minDetectableEffect(
        nPerArm: Int,
        cvPct: Double,
        power: Double = DEFAULT_POWER,
        deff: Double = 1.0,
    ): Double? {
        if (nPerArm <= 0) return null
        val z = Z_ALPHA + zBeta(power)
        val nEff = nPerArm / maxOf(1.0, deff)
        return sqrt(2 * (z * z) * (cvPct * cvPct) / nEff)
    }

    /**
     * `n_for_quantile`: observations needed to pin quantile `p` to ±`relPrecision`, for a log-normal of
     * log-scale shape `sigmaLog`. From Var(q_p) ≈ p(1−p) / (n·f(q_p)²): the density at the quantile
     * governs everything, which is why a production p99 is expensive and a median is not.
     */
    fun nForQuantile(
        sigmaLog: Double,
        p: Double,
        relPrecision: Double,
        deff: Double = 1.0,
        confidenceZ: Double = Z_ALPHA,
    ): Int {
        val zP = Normal.ndtri(p)
        val densityTimesQuantile = exp(-HALF * zP * zP) / (sqrt(2 * PI) * sigmaLog)
        val targetSeRel = relPrecision / confidenceZ
        val n = p * (1 - p) / (densityTimesQuantile * densityTimesQuantile * (targetSeRel * targetSeRel))
        return ceil(n * maxOf(1.0, deff)).toInt()
    }

    /**
     * `n_for_exceedance_rate`: observations to pin "share of launches over X ms" to an absolute
     * precision. Usually the cheaper and more robust way to ask a tail question than an extreme quantile.
     */
    fun nForExceedanceRate(rate: Double, absPrecision: Double, deff: Double = 1.0, confidenceZ: Double = Z_ALPHA): Int =
        ceil(confidenceZ * confidenceZ * rate * (1 - rate) / (absPrecision * absPrecision) * maxOf(1.0, deff)).toInt()

    data class Cuped(val varianceRemoved: Double, val nMultiplier: Double)

    /**
     * `cuped_variance_reduction`: variance falls by ρ² when each unit is adjusted by its own
     * pre-exposure covariate, so the required n falls by the same factor. At ρ = 0.7 roughly half the
     * samples are no longer needed.
     */
    fun cupedVarianceReduction(prePostCorrelation: Double): Cuped {
        val rho = prePostCorrelation.coerceIn(-1.0, 1.0)
        return Cuped(varianceRemoved = rho * rho, nMultiplier = 1 - rho * rho)
    }

    /** `sigma_from_quantile_ratio`: log-normal shape implied by an observable p90/p50 ratio. */
    fun sigmaFromQuantileRatio(p90OverP50: Double): Double = ln(p90OverP50) / Z_90

    /**
     * `dilution`: a change in a component expressed as a change in the whole it sits inside. Measuring
     * an SDK-init change through end-to-end launch time dilutes it before noise is even considered.
     */
    fun dilution(componentSharePct: Double, componentChangePct: Double): Double =
        componentChangePct * componentSharePct / PERCENT

    /** `practical`: statistical significance is not importance; a result must also clear the noise band. */
    fun practical(diffPct: Double, noiseBandPct: Double): Boolean = abs(diffPct) >= noiseBandPct

    private fun zBeta(power: Double): Double = if (power >= DEFAULT_POWER) Z_BETA_80 else Z_BETA_70

    private const val DEFAULT_POWER = 0.80
    private const val Z_ALPHA = 1.959964
    private const val Z_BETA_80 = 0.841621
    private const val Z_BETA_70 = 0.524401
    private const val Z_90 = 1.2815515655446004
    private const val HALF = 0.5
    private const val PERCENT = 100.0
}
