package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber

/**
 * Declares functions for writing a log to the current session span.
 */
interface LogWriter {
    fun addLog(
        schemaType: SchemaType,
        severity: SeverityNumber,
        message: String,
        isPrivate: Boolean = false,
        addCurrentSessionInfo: Boolean = true,
        timestampMs: Long? = null
    )
}
