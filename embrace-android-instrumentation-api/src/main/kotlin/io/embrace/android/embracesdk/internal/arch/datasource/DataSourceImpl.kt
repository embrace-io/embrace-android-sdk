package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Base class for data sources.
 */
abstract class DataSourceImpl(
    args: InstrumentationArgs,
    private val limitStrategy: LimitStrategy,
) : DataSource {

    protected val clock: Clock = args.clock
    protected val logger: EmbLogger = args.logger
    protected val configService: ConfigService = args.configService
    protected val destination: TelemetryDestination = args.destination

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
    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> T?,
    ): T? {
        try {
            if (inputValidation() && limitStrategy.shouldCapture()) {
                return destination.action()
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, exc)
        }
        return null
    }
}
