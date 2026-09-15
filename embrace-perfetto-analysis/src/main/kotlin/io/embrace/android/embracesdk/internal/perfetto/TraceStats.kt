package io.embrace.android.embracesdk.internal.perfetto

import kotlinx.serialization.Serializable

@Serializable
internal data class TraceStats(
    val operations: List<OperationStats>,
    val missing: List<String>,
)
