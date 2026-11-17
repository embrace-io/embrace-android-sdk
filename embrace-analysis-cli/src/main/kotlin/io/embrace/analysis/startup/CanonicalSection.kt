package io.embrace.analysis.startup

/** A canonical SDK-init section: its [name] and its nesting [depth] among the canonical sections. */
data class CanonicalSection(val name: String, val depth: Int)

/**
 * The canonical init sections in execution order (see references/sections.md), with their nesting depth among them.
 * These are the sections exported as `<name>-duration-ms` attributes on the sdk-init span, except the two
 * SharedPreferences sections, which nest wherever the store is first touched and are listed with the others.
 */
val CANONICAL_SECTIONS: List<CanonicalSection> = listOf(
    CanonicalSection("emb-embrace-impl-init", 0),
    CanonicalSection("emb-modules-init", 0),
    CanonicalSection("emb-persisted-config-init", 1),
    CanonicalSection("emb-config-service-init", 1),
    CanonicalSection("emb-startup-classifier-init", 1),
    CanonicalSection("emb-process-state-tracker-init", 1),
    CanonicalSection("emb-otel-tracer-init", 1),
    CanonicalSection("emb-instrumentation-args-init", 1),
    CanonicalSection("emb-payload-storage-service-init", 1),
    CanonicalSection("emb-resource-source-init", 1),
    CanonicalSection("emb-session-orchestrator-init", 1),
    CanonicalSection("emb-current-session-part-span-initialize-service", 1),
    CanonicalSection("emb-process-state-tracker-register", 0),
    CanonicalSection("emb-setup-services", 0),
    CanonicalSection("emb-start-first-session", 1),
    CanonicalSection("emb-post-services-setup", 0),
    CanonicalSection("emb-load-instrumentation", 1),
    CanonicalSection("emb-huc-init", 1),
)

/**
 * Older SDKs (.claude/skills/startup-analysis/references/section-history.md): the names canonical sections had before
 * a rename that left their work unchanged, as current name -> older name, so a trace from an older SDK fills the current
 * section's place. Sections that were split, merged or re-scoped have no entry and are not comparable across the change;
 * references/section-history.md has the full mapping.
 */
val OLDER_SECTION_NAMES: Map<String, String> = mapOf(
    "emb-persisted-config-init" to "emb-persisted-config-load",
    "emb-setup-services" to "emb-post-init",
)
