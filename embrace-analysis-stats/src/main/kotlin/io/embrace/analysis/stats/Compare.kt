package io.embrace.analysis.stats

/**
 * `compare`: the standard comparison for this project - shape, effect size, interval, test, and an
 * explicit statement of what the design can and cannot support. Every version table and A/B verdict
 * in the published analyses came out of this bundle.
 *
 * Rather than keying dictionaries by arm label, this returns typed [Sides] with the labels carried
 * alongside. Every number is computed by the same call in the same order, so the goldens compare
 * field for field.
 */
object Compare {

    const val DEFAULT_NOISE_BAND_PCT: Double = 4.0
    val DEFAULT_QUANTILES: List<Double> = listOf(0.90, 0.95)

    /** A value for each arm; `a` is the baseline / older arm, `b` the candidate / newer one. */
    data class Sides<T>(val a: T, val b: T)

    data class QuantileRow(
        val p: Double,
        val value: Sides<Double>,
        /** Observations sitting beyond the quantile in each arm - what the estimate actually rests on. */
        val support: Sides<Int>,
        /** Cluster-bootstrap CI of the difference, or unavailable with the reason when n is too small. */
        val ci: Cluster.BootstrapResult,
    )

    data class Report(
        val labels: Sides<String>,
        val n: Sides<Int>,
        val clusters: Sides<Int>,
        val median: Sides<Double>,
        val designEffect: Sides<Cluster.DesignEffect>,
        val effectSize: EffectSize.CliffsDelta,
        val medianCi: Cluster.BootstrapResult,
        val permutation: Cluster.PermutationResult,
        /** 100 · (median_b − median_a) / median_a; NaN when the baseline median is zero. */
        val medianDiffPct: Double,
        val clearsNoiseBand: Boolean,
        val quantiles: List<QuantileRow>,
    )

    fun arms(
        a: List<List<Double>>,
        b: List<List<Double>>,
        labelA: String = "A",
        labelB: String = "B",
        noiseBandPct: Double = DEFAULT_NOISE_BAND_PCT,
        quantiles: List<Double> = DEFAULT_QUANTILES,
    ): Report {
        val aFlat = a.flatten().sorted()
        val bFlat = b.flatten().sorted()
        val medianA = Quantile.type7(aFlat, MEDIAN)
        val medianB = Quantile.type7(bFlat, MEDIAN)
        val diffPct = if (medianA != 0.0) PERCENT * (medianB - medianA) / medianA else Double.NaN
        // One set of draws serves the median and every quantile whose n supports an interval: the draws
        // do not depend on the statistic, so asking separately would redraw the same sequence each time.
        val withCi = quantiles.filter { Quantile.ciTrustworthy(aFlat.size, it) && Quantile.ciTrustworthy(bFlat.size, it) }
        val bootstraps = Cluster.bootstrapDiffs(
            a,
            b,
            listOf(Cluster.Statistic.Median) + withCi.map { Cluster.Statistic.Quantile(it) },
        )
        val quantileCis = withCi.zip(bootstraps.drop(1)).toMap()
        return Report(
            labels = Sides(labelA, labelB),
            n = Sides(aFlat.size, bFlat.size),
            clusters = Sides(a.size, b.size),
            median = Sides(medianA, medianB),
            designEffect = Sides(Cluster.designEffect(a), Cluster.designEffect(b)),
            effectSize = EffectSize.cliffsDelta(aFlat, bFlat),
            medianCi = bootstraps.first(),
            permutation = Cluster.permutationTest(a, b),
            medianDiffPct = diffPct,
            clearsNoiseBand = Power.practical(diffPct, noiseBandPct),
            quantiles = quantiles.map { p -> quantileRow(aFlat, bFlat, p, quantileCis[p]) },
        )
    }

    /** [bootstrapped] is the shared-draw interval for this quantile, or null when n is too small for one. */
    private fun quantileRow(
        aFlat: List<Double>,
        bFlat: List<Double>,
        p: Double,
        bootstrapped: Cluster.BootstrapResult?,
    ): QuantileRow {
        val ci = if (bootstrapped != null) {
            bootstrapped
        } else {
            val floor = Quantile.minNForCi[Math.rint(p * PERCENT) / PERCENT]?.toString() ?: "?"
            Cluster.BootstrapResult(
                diff = Quantile.type7(bFlat, p) - Quantile.type7(aFlat, p),
                ci = null,
                available = false,
                reason = "n below the $floor needed before a p${(p * PERCENT).toInt()} interval has usable " +
                    "coverage - point estimate only",
            )
        }
        return QuantileRow(
            p = p,
            value = Sides(Quantile.type7(aFlat, p), Quantile.type7(bFlat, p)),
            support = Sides(Quantile.support(aFlat.size, p), Quantile.support(bFlat.size, p)),
            ci = ci,
        )
    }

    private const val MEDIAN = 0.5
    private const val PERCENT = 100.0
}
