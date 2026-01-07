package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType

/**
 * Base class for data sources.
 */
abstract class DataSourceImpl(
    args: InstrumentationArgs,
    private val limitStrategy: LimitStrategy,
    override val instrumentationName: String,
) : DataSource {

    protected val clock: Clock = args.clock
    protected val logger: InternalLogger = args.logger
    protected val configService: ConfigService = args.configService
    protected val destination: TelemetryDestination = args.destination
    private val telemetryService = args.telemetryService

    override fun onDataCaptureEnabled() {
        // no-op
    }

    override fun onDataCaptureDisabled() {
        // no-op
    }

    @CallSuper
    override fun resetDataCaptureLimits() {
        limitStrategy.resetDataCaptureLimits()
    }

    /**
     * Convenience function for capturing telemetry.
     */
    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        invalidInputCallback: () -> Unit,
        action: TelemetryDestination.() -> T?,
    ): T? {
        try {
            if (!inputValidation()) {
                invalidInputCallback()
            } else if (limitStrategy.shouldCapture()) {
                return destination.action()
            } else {
                // Track that a limit was exceeded
                telemetryService.trackAppliedLimit(instrumentationName, AppliedLimitType.DROP)
            }
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, exc)
        }
        return null
    }
}
