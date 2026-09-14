package io.embrace.analysis.maxims

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.stats.Quantile

/** restore-vs-create: a launch that creates a user session spends longer in start-first-session than one that restores. */
internal object RestoreVsCreateMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "restore-vs-create",
        scope = Maxims.UNIVERSAL,
        statement = "A launch that creates a user session spends at least 2x as long in start-first-session as one " +
            "that restores the persisted session.",
        why = "The create path serializes and persists session metadata on the main thread; the restore path is a " +
            "read. The bench measured only the restore path until the cohort tap existed. An arm that pins the " +
            "cohort cannot test it - the comparison is then across the matching cells of the two arms, not inside " +
            "one campaign, and the check reports n/a rather than pretending the sample is merely thin.",
        check = ::checkRestoreVsCreate,
    )

    private fun checkRestoreVsCreate(c: Maxims.Campaign): Maxims.Verdict {
        if (c.cohorts.isEmpty()) {
            return Maxims.Verdict(Maxims.NA, "no passN-cohorts.json (EmbVerify tap not armed)")
        }
        val created = c.cohorts.filter { PyJson.strOrNull(it, "cohort") == "created" }
            .mapNotNull { LaunchAttrs.attrFloat(it, Maxims.FIRST_SESSION_ATTR) }
        val restored = c.cohorts.filter { PyJson.strOrNull(it, "cohort") == "restored" }
            .mapNotNull { LaunchAttrs.attrFloat(it, Maxims.FIRST_SESSION_ATTR) }
        if (created.isEmpty() && restored.isEmpty()) {
            return Maxims.Verdict(Maxims.NA, "${Maxims.FIRST_SESSION_ATTR} absent from the tap")
        }
        // An arm that pins the cohort holds one side of the comparison at zero by construction, so no
        // number of extra launches would make it testable: it needs the matching cell of the other arm.
        if (created.isEmpty() || restored.isEmpty()) {
            return Maxims.Verdict(
                Maxims.NA,
                "single-cohort arm (${created.size} created, ${restored.size} restored); " +
                    "compare against the matching cell of the other arm",
            )
        }
        if (created.size < MIN_COHORT || restored.size < MIN_COHORT) {
            return Maxims.Verdict(
                Maxims.THIN,
                "${created.size} created and ${restored.size} restored launches with the attribute",
            )
        }
        val cMed = Quantile.median(created.sorted())
        val rMed = Quantile.median(restored.sorted())
        val obs = "created p50 ${PyFormat.fixed(cMed, 2)} ms (n=${created.size}) vs " +
            "restored p50 ${PyFormat.fixed(rMed, 2)} ms (n=${restored.size})"
        val ok = cMed >= 2.0 * rMed && (cMed - rMed) >= CREATE_MIN_GAP_MS
        return Maxims.Verdict(if (ok) Maxims.CONFIRMED else Maxims.CONTRADICTED, obs)
    }

    private const val MIN_COHORT = 5
    private const val CREATE_MIN_GAP_MS = 0.5
}
