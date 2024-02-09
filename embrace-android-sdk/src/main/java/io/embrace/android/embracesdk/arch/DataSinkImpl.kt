package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

internal class DataSinkImpl<in T : SpanEventMapper, S>(
    private val openTelemetryClock: OpenTelemetryClock,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataSink<T, S> {

    override fun addEvent(dataToStore: T) {
        try {
            dataToStore.toSpanEvent(openTelemetryClock.nanoTime())
            // TODO: add to the session span here.
        } catch (exc: Throwable) {
            logger.logError("Failed to store data", exc)
        }
    }

    override fun getSnapshot(): S {
        try {
            TODO("Not yet implemented")
        } catch (exc: Throwable) {
            logger.logError("Failed to generated snapshot", exc)
            throw exc
        }
    }

    override fun flush(): S {
        try {
            TODO("Not yet implemented")
        } catch (exc: Throwable) {
            logger.logError("Failed to flush data", exc)
            throw exc
        }
    }
}
