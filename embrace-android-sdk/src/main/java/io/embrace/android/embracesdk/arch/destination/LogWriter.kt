package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.schema.SchemaType

/**
 * Declares functions for writing a log to the current session span.
 */
internal interface LogWriter {
    fun addLog(
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean = false
    )
}
