package io.embrace.android.embracesdk.internal.perfetto

internal data class TraceStats(
    val operations: List<OperationStats>,
    val missing: List<String>,
)
