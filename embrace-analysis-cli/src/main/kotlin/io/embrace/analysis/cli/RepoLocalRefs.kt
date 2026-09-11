package io.embrace.analysis.cli

import io.embrace.analysis.localrefs.LocalRefsCheck

/**
 * This repository's layout for the local-refs sweep, and the only place it is written down. The
 * checker itself takes [LocalRefsCheck.Roots] and knows no layout; this is what `check-local-refs`
 * hands it.
 *
 * Source (held to every pattern): the skills' prose, the wrapper, and every `embrace-analysis-*`
 * module's main sources and README. Data (held only to the personal patterns, since citing a real run
 * is their job): the committed records and the frozen fixtures. The `records` directory sits under the
 * skills, so the source walk skips it and the data walk covers it; the fixtures module splits the same
 * way, its helper code as source and its `fixtures/` tree as data.
 */
object RepoLocalRefs {

    val ROOTS: LocalRefsCheck.Roots by lazy {
        LocalRefsCheck.Roots(
            source = listOf(".claude/skills", "tools") +
                ANALYSIS_MODULES.flatMap { listOf("embrace-analysis-$it/src/main", "embrace-analysis-$it/README.md") } +
                listOf("embrace-analysis-test-fixtures/src/main/kotlin", "embrace-analysis-test-fixtures/README.md"),
            data = listOf(".claude/skills/_shared/records", "embrace-analysis-test-fixtures/fixtures"),
            skipInSource = setOf("records"),
        )
    }

    /** The library and executable modules; the fixtures module is listed separately because it splits. */
    private val ANALYSIS_MODULES = listOf(
        "common", "stats", "perfetto", "device", "local-refs", "records", "reports", "maxims", "campaign", "cli",
    )
}
