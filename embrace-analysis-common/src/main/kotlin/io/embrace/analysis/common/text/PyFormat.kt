package io.embrace.analysis.common.text

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Number formatting that reproduces Python's `format()` where the two runtimes differ.
 *
 * Python's `f"{x:.2f}"` rounds the EXACT binary value of the double half-to-even; Java's
 * `String.format("%.2f")` rounds it half-up. They disagree only on exact ties (`0.125` at two
 * decimals), which are rare in measured data but common in synthetic fixtures - and a report golden
 * that differs by one unit in the last digit is a false alarm nobody should have to investigate.
 * Everything here goes through `BigDecimal(x)` - the exact value - with HALF_EVEN.
 */
object PyFormat {

    /** `f"{x:.{decimals}f}"`. NaN and infinities print as Python does (`nan`, `inf`, `-inf`). */
    fun fixed(x: Double, decimals: Int): String {
        if (x.isNaN()) return "nan"
        if (x.isInfinite()) return if (x > 0) "inf" else "-inf"
        val scaled = BigDecimal(x).setScale(decimals, RoundingMode.HALF_EVEN)
        val text = scaled.toPlainString()
        // Python keeps the sign of a negative value (or -0.0) that rounds to zero; BigDecimal drops it.
        return if (x.compareTo(0.0) < 0 && scaled.signum() == 0) "-$text" else text
    }

    /** `f"{x:+.{decimals}f}"`: always signed. */
    fun fixedSigned(x: Double, decimals: Int): String {
        val text = fixed(x, decimals)
        return if (text.startsWith("-") || x.isNaN()) text else "+$text"
    }

    /** `f"{x:.0%}"` - a ratio as a whole-number percentage with the sign Python would print. */
    fun percent0(x: Double): String = fixed(x * PERCENT, 0) + "%"

    private const val PERCENT = 100.0
}
