package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

/**
 * Declares functions for writing a log to the current session span.
 */
@InternalApi
public interface LogWriter {
    public fun addLog(
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean = false
    )
}
