package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/** The whole of what one iteration recorded: a section's total in nanoseconds, or a counter's. */
@Serializable
internal data class IterationValue(val iteration: Int, val value: Long)
