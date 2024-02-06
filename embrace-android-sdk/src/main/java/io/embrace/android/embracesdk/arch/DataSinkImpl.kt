package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.spans.ErrorCode

internal class DataSinkImpl(
    private val tracer: EmbraceTracer,
    private val spansService: SpansService,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : DataSink {

    override fun mutateSpan(spanId: String, action: SpanMutator) {
        val span = tracer.getSpan(spanId)

        if (span == null) {
            logger.logWarning("Span not found for id: $spanId")
            return
        }
        try {
            action(span)
        } catch (exc: Throwable) {
            logger.logError("Exception thrown while mutating span", exc)
        }
    }

    override fun mutateSessionSpan(action: SpanMutator) {
        val sessionSpanId = "FIXME" // TODO: pass the correct session span ID
        mutateSpan(sessionSpanId, action)
    }

    override fun startSpan(name: String, action: SpanMutator): String? {
        val span = tracer.createSpan(name)

        if (span == null) {
            logger.logWarning("Failed to create span named: $name")
            return null
        }
        try {
            action(span)
        } catch (exc: Throwable) {
            logger.logError("Exception thrown while starting span", exc)
        }
        return span.spanId
    }

    override fun stopSpan(spanId: String, action: SpanMutator): Boolean {
        val span = tracer.getSpan(spanId)

        if (span == null) {
            logger.logWarning("Failed to find & stop span: $spanId")
            return false
        }
        return try {
            action(span)
            span.stop()
        } catch (exc: Throwable) {
            logger.logError("Exception thrown while stopping span", exc)
            span.stop(ErrorCode.FAILURE)
            false
        }
    }
}
