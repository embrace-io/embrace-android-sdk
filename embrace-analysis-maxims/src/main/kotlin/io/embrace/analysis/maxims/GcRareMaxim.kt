package io.embrace.analysis.maxims

import io.embrace.analysis.common.text.PyFormat

/** gc-rare: the SDK's own GC almost never runs during init, above the low-RAM tier. */
internal object GcRareMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "gc-rare",
        scope = Maxims.UNIVERSAL,
        statement = "The SDK's own GC almost never runs during init: a collection of 1 ms or more inside the window on " +
            "2% or fewer of iterations on any device above 2 GB of RAM.",
        why = "Own-process collection during init was only ever seen on the 1 GB tier; above it a zero is the device, " +
            "not the detector. Production sees 0.02 to 0.3%. The 1 ms floor keeps sub-millisecond class-load slices " +
            "misread as GC in older datasets from counting.",
        check = ::checkGcRare,
    )

    private fun checkGcRare(c: Maxims.Campaign): Maxims.Verdict {
        if (c.ramClass == Maxims.LOW_RAM_CLASS) {
            return Maxims.Verdict(Maxims.NA, "out of scope on a ${Maxims.LOW_RAM_CLASS} device")
        }
        if (!c.hasFactors) {
            return Maxims.Verdict(Maxims.NA, "no passN-factors.json")
        }
        val n = c.iterations.size
        val withGc = c.iterations.count { it.gc == true }
        val share = withGc.toDouble() / n
        return Maxims.Verdict(
            if (share <= GC_SHARE_MAX) Maxims.CONFIRMED else Maxims.CONTRADICTED,
            "${PyFormat.fixed(PERCENT * share, 1)}% of $n iterations ran own GC",
        )
    }

    private const val GC_SHARE_MAX = 0.02
    private const val PERCENT = 100.0
}
