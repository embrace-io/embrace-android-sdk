package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.opentelemetry.api.common.AttributeKey

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
interface LogService : MemoryCleanerListener {

    /**
     * Creates a remote log.
     */
    fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>? = null,
        customLogAttrs: Map<AttributeKey<String>, String> = emptyMap(),
        logAttachment: Attachment.EmbraceHosted? = null,
    )

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    fun getErrorLogsCount(): Int
}
