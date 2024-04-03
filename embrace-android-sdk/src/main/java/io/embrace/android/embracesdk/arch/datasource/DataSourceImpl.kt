package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

/**
 * Base class for data sources.
 */
internal abstract class DataSourceImpl<T>(
    private val destination: T,
    private val logger: InternalEmbraceLogger,
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

    override fun alterSessionSpan(
        inputValidation: () -> Boolean,
        captureAction: T.() -> Unit
    ): Boolean = captureDataImpl(inputValidation, captureAction, true)

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

/**
 * Syntactic sugar for when no input validation is required. Developers must explicitly state
 * this is the case.
 */
internal val NoInputValidation = { true }
