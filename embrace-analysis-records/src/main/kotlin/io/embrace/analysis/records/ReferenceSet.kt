package io.embrace.analysis.records

import io.embrace.analysis.device.DeviceProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The reference device set (`reference-set.json`): the stable device keys every longitudinal report
 * is keyed on, each device's identity profile (checked for drift at ingest), and the frozen recipe.
 *
 * `device_key` values are the user's labels and are kept forever - they are the axis of every
 * longitudinal comparison, so renaming one is equivalent to retiring the device. The `_comment`
 * arrays in the file are documentation and are ignored on read.
 */
@Serializable
data class ReferenceSet(
    @SerialName("declared_at") val declaredAt: String,
    val recipe: RecipeSpec,
    val devices: Map<String, ReferenceDevice>,
)

/** The recipe as declared (no tool provenance yet - that is added per record at ingest). */
@Serializable
data class RecipeSpec(
    @SerialName("run_shape") val runShape: RunShape,
    @SerialName("build_type") val buildType: String,
    @SerialName("compile_state") val compileState: String,
    /** Deliberately nullable: unset means "not yet decided", and ingest must refuse, not default. */
    val instrument: String? = null,
)

@Serializable
data class ReferenceDevice(
    val serial: String,
    /** A guess from RAM and cluster topology at probe time; confirmed by hand. */
    val tier: String,
    val profile: DeviceProfile,
    /** Per-device cool gate in °C - a plugged-in device's warm idle floor can sit above a naive gate. */
    @SerialName("cool_gate_c") val coolGateC: Double,
    val retired: Boolean = false,
)
