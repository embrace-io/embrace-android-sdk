package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Base class for data sources.
 */
internal abstract class DataSourceImpl<T>(
    private val destination: T,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataSource<T> {

    override fun enableDataCapture() {
        // no-op
    }

    override fun disableDataCapture() {
        // no-op
    }

    override fun resetDataCaptureLimits() {
        // no-op
    }

    override fun captureData(action: T.() -> Unit) {
        try {
            destination.action()
        } catch (exc: Throwable) {
            logger.logError("Error capturing data", exc)
        }
    }
}
