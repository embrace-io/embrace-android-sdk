package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * An abstract implementation of [DataSource] that captures data and writes it to a [DataSink].
 * This base class contains convenience functions for capturing data that makes the syntax nicer
 * in subclasses.
 */
internal abstract class DataSourceImpl<T : SpanEventMapper, S>(
    private val sink: DataSinkProvider<T, S>,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataSource<T, S> {

    override fun captureData(action: DataSinkMutator<T, S>) {
        try {
            action(sink())
        } catch (exc: Throwable) {
            logger.logError("Failed to capture data", exc)
        }
    }
}
