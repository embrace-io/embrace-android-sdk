package io.embrace.android.embracesdk.internal.perfetto

import kotlinx.serialization.Serializable

/**
 * One value a counter took, [offsetNanos] after the trace's first ftrace event.
 */
@Serializable
internal data class CounterReading(val tid: Int, val offsetNanos: Long, val value: Long)
