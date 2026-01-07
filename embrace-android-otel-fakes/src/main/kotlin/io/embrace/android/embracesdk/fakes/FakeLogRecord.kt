package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
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
