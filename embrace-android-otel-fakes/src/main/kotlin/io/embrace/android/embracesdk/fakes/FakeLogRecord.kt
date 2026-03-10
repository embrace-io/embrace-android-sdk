package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.model.SeverityNumber

class FakeLogRecord(
    val eventName: String?,
    val body: String?,
    val timestamp: Long?,
    val observedTimestamp: Long?,
    val context: Context?,
    val severityNumber: SeverityNumber?,
    val severityText: String?,
    val attributes: Map<String, Any>,
)
