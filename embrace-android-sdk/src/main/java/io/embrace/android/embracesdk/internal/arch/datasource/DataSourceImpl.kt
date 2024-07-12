package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Base class for data sources.
 */
public abstract class DataSourceImpl<T>(
    private val destination: T,
    private val logger: EmbLogger,
    private val limitStrategy: LimitStrategy,
) : DataSource<T> {

    override fun enableDataCapture() {
        // no-op
    }

    override fun disableDataCapture() {
        // no-op
    }

    override fun resetDataCaptureLimits() {
        limitStrategy.resetDataCaptureLimits()
    }

    override fun captureData(
        inputValidation: () -> Boolean,
        captureAction: T.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction)

    protected fun captureDataImpl(
        inputValidation: () -> Boolean,
        captureAction: T.() -> Unit,
        enforceLimits: Boolean = true
    ): Boolean {
        try {
            if (enforceLimits && !limitStrategy.shouldCapture()) {
                logger.logInfo(
                    "Data capture limit reached for ${this.javaClass.name}." +
                        " Ignoring to keep payload size reasonable - other data types will still be captured normally."
                )
                return false
            }
            if (!inputValidation()) {
                logger.logWarning("Input validation failed.")
                return false
            }
            destination.captureAction()
            return true
        } catch (exc: Throwable) {
            logger.logError("Error capturing data", exc)
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, exc)

            return false
        }
    }
}
