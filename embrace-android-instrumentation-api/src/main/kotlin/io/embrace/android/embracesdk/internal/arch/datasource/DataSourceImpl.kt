package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Base class for data sources.
 */
abstract class DataSourceImpl(
    private val destination: TelemetryDestination,
    val logger: EmbLogger,
    private val limitStrategy: LimitStrategy,
) : DataSource {

    override fun onDataCaptureEnabled() {
        // no-op
    }

    override fun onDataCaptureDisabled() {
        // no-op
    }

    override fun resetDataCaptureLimits() {
        limitStrategy.resetDataCaptureLimits()
    }

    /**
     * Convenience function for capturing telemetry.
     */
    override fun captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> Unit,
    ) {
        try {
            if (inputValidation() && limitStrategy.shouldCapture()) {
                destination.action()
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, exc)
        }
    }
}
