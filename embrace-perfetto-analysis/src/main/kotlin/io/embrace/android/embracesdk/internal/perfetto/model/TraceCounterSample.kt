package io.embrace.android.embracesdk.internal.perfetto.model

/**
 * One value an atrace counter took, at the instant the trace recorded it.
 */
internal data class TraceCounterSample(
    val name: String,
    val tid: Int,
    val timestampNanos: Long,
    val value: Long,
)
