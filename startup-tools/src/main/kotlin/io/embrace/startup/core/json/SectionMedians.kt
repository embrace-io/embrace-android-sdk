package io.embrace.startup.core.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-section medians for one device × version arm, as the attribution campaigns reduced
 * them from traces before the traces were deleted. The window itself is stored under the reserved key
 * [WINDOW_KEY] so a section's share of the window can be computed without a second file.
 *
 * Sections NEST (`modules-init` contains `config-service-init`, `span-service-init` contains
 * `otel-tracer-init`, …), so medians are never additive: summing them "as shares of the gain" once
 * produced a report claiming 194% of an improvement. Containment must be measured from intervals,
 * and separately per version - across 9.0.0 → 9.2.0 the names themselves changed from minified
 * class names to literals.
 */
@Serializable
data class SectionMedians(
    val device: String,
    @SerialName("measured_at") val measuredAt: String,
    /** Section name → median duration in ms; includes [WINDOW_KEY]. */
    val medians: Map<String, Double>,
) {
    val windowMs: Double? get() = medians[WINDOW_KEY]

    /** Sections only, with the window entry removed. */
    val sections: Map<String, Double> get() = medians.filterKeys { it != WINDOW_KEY }

    companion object {
        const val WINDOW_KEY: String = "__window__"
    }
}
