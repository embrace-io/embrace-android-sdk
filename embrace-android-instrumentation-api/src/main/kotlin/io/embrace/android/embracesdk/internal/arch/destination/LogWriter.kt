package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Declares functions for writing a log to the current session span.
 */
interface LogWriter {
    fun addLog(
        schemaType: SchemaType,
        severity: LogSeverity,
        message: String,
        isPrivate: Boolean = false,
        addCurrentSessionInfo: Boolean = true,
        timestampMs: Long? = null
    )
}
