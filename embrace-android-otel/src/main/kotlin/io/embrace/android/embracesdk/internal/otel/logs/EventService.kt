package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.Initializable
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.SeverityNumber

/**
 * An OTel-agnostic API to create telemetry modeled as OTel LogRecords aka Events
 */
interface EventService : Initializable {
    /**
     * Records an event using the SDK's OTel Logger instance. The emitted log record is enriched with
     * a unique ID and, unless [addCurrentMetadata] is false, a snapshot of the current SDK metadata
     * by [EmbraceLogRecordProcessor].
     */
    fun log(
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        eventAttributes: (AttributesMutator.() -> Unit)?,
    )
}
