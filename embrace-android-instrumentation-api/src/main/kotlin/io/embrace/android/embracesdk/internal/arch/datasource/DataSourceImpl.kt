package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
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
    protected val errorHandler: ErrorHandler = args.errorHandler
    protected val configService: ConfigService = args.configService
    protected val destination: TelemetryDestination = args.destination
    protected val telemetryService = args.telemetryService

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
    ): T? = errorHandler.guardSdkCode(SdkErrorType.DataSourceDataCaptureFail, null) {
        if (!inputValidation()) {
            invalidInputCallback()
            null
        } else if (limitStrategy.shouldCapture()) {
            destination.action()
        } else {
            // Track that a limit was exceeded
            telemetryService.trackAppliedLimit(instrumentationName, AppliedLimitType.DROP)
            null
        }
    }
}
