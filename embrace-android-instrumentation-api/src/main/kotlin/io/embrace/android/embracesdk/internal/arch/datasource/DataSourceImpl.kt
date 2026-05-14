package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for data sources whose lifecycles are managed by [DataSourceState] which contain logic for initialization.
 *
 * [enableOnCreate] can be overridden to opt-out of enablement at creation time by [DataSourceState].
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
    protected val telemetryService = args.telemetryService
    private val enabled = AtomicBoolean(false)

    /**
     * Whether [enable] should be invoked automatically when this data source is created by [DataSourceState]. Defaults to true.
     * For data sources where this is false, enablement will happen at a later time at the discretion of the data source.
     */
    open val enableOnCreate: Boolean = true

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
        if (!isEnabled()) {
            enable()
        }
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
            logger.trackInternalError(InternalErrorType.DataSourceDataCaptureFail, exc)
        }
        return null
    }

    /**
     * Whether this data source has been enabled. If this is false, this data source should not record any telemetry.
     */
    fun isEnabled(): Boolean = enabled.get()

    /**
     * Enables this data source and invokes [onDataCaptureEnabled]. This method is a no-op after the first invocation.
     */
    fun enable() {
        if (!enabled.getAndSet(true)) {
            onDataCaptureEnabled()
        }
    }
}
