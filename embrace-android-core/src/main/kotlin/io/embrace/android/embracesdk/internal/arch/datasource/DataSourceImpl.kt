package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Base class for data sources.
 */
abstract class DataSourceImpl<T>(
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
        captureAction: T.() -> Unit,
    ): Boolean = captureDataImpl(inputValidation, captureAction)

    protected fun captureDataImpl(
        inputValidation: () -> Boolean,
        captureAction: T.() -> Unit,
        enforceLimits: Boolean = true,
    ): Boolean {
        try {
            if (enforceLimits && !limitStrategy.shouldCapture()) {
                return false
            }
            if (!inputValidation()) {
                return false
            }
            destination.captureAction()
            return true
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, exc)
            return false
        }
    }
}
