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

    override fun captureData(inputValidation: () -> Boolean, captureAction: T.() -> Unit) {
        try {
            if (!limitStrategy.shouldCapture()) {
                logger.logWarning("Data capture limit reached.")
                return
            }
            if (!inputValidation()) {
                logger.logWarning("Input validation failed.")
                return
            }
            destination.captureAction()
        } catch (exc: Throwable) {
            logger.logError("Error capturing data", exc)
        }
    }
}
