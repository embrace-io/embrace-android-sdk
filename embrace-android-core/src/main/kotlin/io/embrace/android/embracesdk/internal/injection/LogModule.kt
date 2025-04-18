package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
interface LogModule {
    val networkCaptureService: NetworkCaptureService
    val networkCaptureDataSource: NetworkCaptureDataSource
    val networkLoggingService: NetworkLoggingService
    val logService: LogService
    val logOrchestrator: LogOrchestrator
    val attachmentService: AttachmentService
}
