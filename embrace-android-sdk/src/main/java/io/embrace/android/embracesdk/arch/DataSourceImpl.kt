package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Base class for data sources.
 */
internal abstract class DataSourceImpl<T>(
    private val destination: T,
    private val limitStrategy: LimitStrategy,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
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

    override fun captureData(inputValidation: () -> Boolean, captureAction: T.() -> Unit): Boolean {
        return captureDataImpl(inputValidation, captureAction)
    }

    protected fun captureDataImpl(
        inputValidation: () -> Boolean,
        captureAction: T.() -> Unit,
        enforceLimits: Boolean = true
    ): Boolean {
        try {
            if (enforceLimits && !limitStrategy.shouldCapture()) {
                logger.logWarning("Data capture limit reached.")
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
            return false
        }
    }
}
