package io.embrace.analysis.maxims

/** cpu-closure: the init span's CPU attributes (init-cpu-pct + init-run-delay-pct) close to 100%. */
internal object CpuClosureMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "cpu-closure",
        scope = Maxims.UNIVERSAL,
        statement = "The init span's CPU attributes close: init-cpu-pct plus init-run-delay-pct is between 0 and 100 " +
            "on at least 98% of launches.",
        why = "The one attribute check that needs no ground truth; a violation means the attributes and the trace " +
            "disagree and the run is not trustworthy.",
        check = ::checkCpuClosure,
    )

    private fun checkCpuClosure(c: Maxims.Campaign): Maxims.Verdict {
        if (c.cohorts.isEmpty()) {
            return Maxims.Verdict(Maxims.NA, "no passN-cohorts.json (EmbVerify tap not armed)")
        }
        val pairs = c.cohorts.mapNotNull { l ->
            val a = LaunchAttrs.attrFloat(l, Maxims.CPU_ATTR)
            val b = LaunchAttrs.attrFloat(l, Maxims.DELAY_ATTR)
            if (a != null && b != null) a to b else null
        }
        if (pairs.isEmpty()) {
            return Maxims.Verdict(Maxims.NA, "${Maxims.CPU_ATTR} / ${Maxims.DELAY_ATTR} absent from the tap")
        }
        if (pairs.size < Maxims.MIN_GROUP) {
            return Maxims.Verdict(Maxims.THIN, "${pairs.size} launches carry both attributes")
        }
        val ok = pairs.count { (a, b) ->
            val residual = PERCENT - a - b
            residual >= 0.0 && residual <= PERCENT
        }
        val obs = "$ok/${pairs.size} launches close to 100%"
        return Maxims.Verdict(if (ok.toDouble() / pairs.size >= CLOSURE_MIN_SHARE) Maxims.CONFIRMED else Maxims.CONTRADICTED, obs)
    }

    private const val CLOSURE_MIN_SHARE = 0.98
    private const val PERCENT = 100.0
}
