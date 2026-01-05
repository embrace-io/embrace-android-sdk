package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
interface LogModule {
    val logService: LogService
    val logOrchestrator: LogOrchestrator
    val attachmentService: AttachmentService
    val logLimitingService: LogLimitingService
}
