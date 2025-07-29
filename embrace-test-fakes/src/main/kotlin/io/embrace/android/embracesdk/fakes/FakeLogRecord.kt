package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeLogRecord(
    val observedTimestamp: Long?,
    val severityNumber: SeverityNumber?,
    val timestamp: Long?,
    val body: String?,
    val context: Context?,
    val severityText: String?,
    val attributes: Map<String, Any>,
)
