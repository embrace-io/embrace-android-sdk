package io.embrace.gradle.configmodel

/**
 * Where an option is read from in the remote config. A [rollout] value is a percentage of devices, and the option
 * is enabled on this device if it falls within it. A value outside [validRange] is ignored, and one outside
 * [clamp] is clamped to it.
 */
data class RemoteSourceSpec(
    val path: String,
    val rollout: Boolean,
    val clamp: ValueRangeSpec?,
    val validRange: ValueRangeSpec?,
)
