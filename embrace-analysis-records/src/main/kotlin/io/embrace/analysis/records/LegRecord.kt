package io.embrace.analysis.records

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One leg of an A/B campaign as the drivers preserve it the moment the leg completes: every launch's
 * window, and (in drivers fixed after the first engine-tail campaign) every section's median. A leg IS a cluster for inference -
 * one install, one pass of consecutive launches sharing thermal state - so the analysis resamples and
 * relabels legs, never launches.
 *
 * The section medians exist so the PROPAGATION GATE can run after the traces are gone: a flag A/B is
 * only interpretable if the effect concentrates in the sections the flag targets while the control
 * section holds steady, and that campaign could not show it because its driver kept windows only.
 */
@Serializable
data class LegRecord(
    val device: String,
    val arm: String,
    val leg: Int,
    @SerialName("iterations_declared") val iterationsDeclared: Int,
    @SerialName("windows_ms") val windowsMs: List<Double>,
    @SerialName("section_medians_ms") val sectionMediansMs: Map<String, Double>? = null,
    @SerialName("measured_at") val measuredAt: String,
)
