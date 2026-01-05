package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * An OTel-agnostic API to create telemetry modeled as OTel LogRecords aka Events
 */
interface EventService {
    fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        embraceAttributes: Map<String, String>
    )
}
