package io.embrace.gradle.configmodel

/**
 * An inclusive range a numeric remote value is clamped to or must fall within. Either bound may be open.
 */
data class ValueRangeSpec(
    val min: Number?,
    val max: Number?,
)
