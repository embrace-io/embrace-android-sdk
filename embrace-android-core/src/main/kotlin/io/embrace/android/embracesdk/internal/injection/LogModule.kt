package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService

/**
 * Holds dependencies that are required for a customer to send log messages to the backend.
 */
public interface LogModule {
    public val networkCaptureService: NetworkCaptureService
    public val networkCaptureDataSource: NetworkCaptureDataSource
    public val networkLoggingService: NetworkLoggingService
    public val logService: LogService
    public val logOrchestrator: LogOrchestrator
}
